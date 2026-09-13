import os
p = os.path.expanduser('~/ow-models/etexport/.venv/lib/python3.12/site-packages/executorch/examples/models/llama/source_transformation/quantize.py')
s = open(p).read()
old = '''        def filter_fn(m, fqn):
            if not isinstance(m, nn.Linear):
                return False
            parts = fqn.split(".")
            if "lora_a" in parts or "lora_b" in parts:
                return False
            if group_size == 0:
                return True
            return m.weight.shape[1] % group_size == 0
'''
new = '''        # OpenWeights bisection: linears whose name matches OW_INT8_REGEX get int8
        # per-channel weights (XNNPACK qd8 x qc8w) instead of int4 groups; ".*" for all.
        _int8_regex = os.environ.get("OW_INT8_REGEX")

        def _int8_match(fqn):
            return bool(_int8_regex) and __import__("re").search(_int8_regex, fqn) is not None

        def filter_fn(m, fqn):
            if not isinstance(m, nn.Linear):
                return False
            parts = fqn.split(".")
            if "lora_a" in parts or "lora_b" in parts:
                return False
            if _int8_match(fqn):
                return False
            if group_size == 0:
                return True
            return m.weight.shape[1] % group_size == 0
'''
assert s.count(old) == 1, s.count(old)
s = s.replace(old, new)
old2 = '''        # OpenWeights: put a QAD Q4_0 GGUF's own codes and scales into the int4 tensors.'''
new2 = '''        if _int8_regex:
            quantize_(
                model,
                Int8DynamicActivationIntxWeightConfig(
                    weight_dtype=torch.int8,
                    weight_granularity=PerAxis(0),
                    intx_choose_qparams_algorithm="affine",
                ),
                filter_fn=lambda m, fqn: isinstance(m, nn.Linear) and _int8_match(fqn),
            )
            print("ow_mix: int8 per-channel on linears matching", _int8_regex)
        # OpenWeights: put a QAD Q4_0 GGUF's own codes and scales into the int4 tensors.'''
assert s.count(old2) == 1
s = s.replace(old2, new2)
open(p, 'w').write(s)
print('mix patch applied')
