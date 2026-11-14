Title: CPU: decode with context in the KV cache is up to 25% slower on Android ARM since #31854 and #29810

### Name and Version

v1.0.0 (b1b770d9) against v0.8.0 (8642541b). Android arm64 builds with `GGML_BACKEND_DL=ON`,
`GGML_CPU_ALL_VARIANTS=ON`, `GGML_CPU_KLEIDIAI=ON`, `GGML_NATIVE=OFF`; F16 KV cache, flash attention
on, 4 threads.

### Problem

Decode slows as context grows, on every ARM phone we measured. With an empty context the two
versions are level. #29810 measured about 4% at 4k on an Apple M5 for the V change, and #31854
measured 1.1-1.5x for F16 MUL_MAT on an M5 Max. On phones the combined cost at depth is much
larger.

llama-bench, `-p 0 -n 64 -d 0,2048,4096 -fa 1 -t 4`, v0.8.0 / v1.0.0 / v1.0.0+patch (below) in
rotating order, four rounds, a cooling gate before each run. Decode relative to v0.8.0, medians;
v1.0.0 was slower than v0.8.0 in every round at every depth shown:

| Device (CPU variant) | Model | d2048 v1.0.0 | d4096 v1.0.0 | d2048 patched | d4096 patched |
|---|---|---|---|---|---|
| POCO X8 Pro Max, Dimensity 9500s (armv9.0_1) | LFM2.5-1.2B QAD-Q4_0 | 0.833 | 0.789 | 0.920 | 0.913 |
| same | Qwen3-1.7B Q8_0 | **0.747** | **0.752** | 0.978 | 1.038 |
| Snapdragon 8 Gen 2 (armv8.6_1) | LFM2.5-1.2B QAD-Q4_0 | 0.898 | 0.854 | 0.937 | 0.913 |
| same | Qwen3-1.7B Q8_0 | 0.975 | 0.930 | 1.044 | 1.045 |
| Apple M5, native | LFM2.5-1.2B QAD-Q4_0 | 0.914 | 0.904 | 0.956 | 0.942 |
| same | Qwen3-1.7B Q8_0 | 0.824 | 0.780 | 0.873 | 0.842 |

At depth 0 every ratio is within 0.95-1.02.

An application's own engine shows the same thing. On Galaxy S24s (Snapdragon 8 Gen 3, armv8.6_1),
with one load, the context filled to each depth and 96 tokens decoded, two devices per version, decode
was 0.93 / 0.80 / 0.77 / 0.88 / 0.93 of v0.8.0 at 35 / 1,480 / 5,832 / 11,629 / 23,240 tokens. At all
five depths every v1.0.0 device was slower than every v0.8.0 device.

### What helps

The patch restores the F16 dot for F16 K in `ggml_compute_forward_flash_attn_ext_f16_one_chunk` on
CPUs with native F16 arithmetic. It converts Q with `ggml_cpu_fp32_to_fp16` and uses `kq_vec_dot =
ggml_vec_dot_f16`, guarded by `__ARM_FEATURE_FP16_VECTOR_ARITHMETIC`. V still accumulates in F32, so
#29774 stays fixed. Results:

- `test-backend-ops -o FLASH_ATTN_EXT` against Metal: all 5,353 cases pass, as on stock v1.0.0.
- Qwen3 recovers fully on both phones (0.98-1.04).
- LFM2.5 recovers to 0.91-0.94. Its remaining gap may be the F32 V accumulator being read and written
  for every key. A decode path that keeps VKQ in registers across a block of keys, or F16 products
  with F32 accumulation via FEAT_FHM (`vfmlalq_low/high_f16`), might close that without giving up
  F32 accumulation.

Patch:

```diff
-    ggml_type         const k_vec_dot_type = ggml_get_type_traits_cpu(k->type)->vec_dot_type;
+#if defined(__ARM_FEATURE_FP16_VECTOR_ARITHMETIC)
+    const bool kq_f16 = k->type == GGML_TYPE_F16;
+#else
+    const bool kq_f16 = false;
+#endif
+    ggml_type         const k_vec_dot_type = kq_f16 ? GGML_TYPE_F16 : ggml_get_type_traits_cpu(k->type)->vec_dot_type;
     ggml_from_float_t const q_to_vec_dot   = ggml_get_type_traits_cpu(k_vec_dot_type)->from_float;
-    ggml_vec_dot_t    const kq_vec_dot     = ggml_get_type_traits_cpu(k->type)->vec_dot;
+    ggml_vec_dot_t    const kq_vec_dot     = kq_f16 ? (ggml_vec_dot_t) ggml_vec_dot_f16 : ggml_get_type_traits_cpu(k->type)->vec_dot;
```

Happy to run candidate fixes on these phones.

### First Bad Commit

Not bisected. #31854 (F32 src1, `vec_dot_type` of F16 is F32) and #29810 (F32 V accumulation in
the one-chunk FA kernel) are the two changes in this path between the versions.
