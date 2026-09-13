import os
p = os.path.expanduser('~/ow-models/etexport/.venv/lib/python3.12/site-packages/executorch/examples/models/llama/source_transformation/quantize.py')
s = open(p).read()
old = '''            filter_fn=filter_fn,
        )
        # TODO: deal with checkpoint / computation dtype decoupling.'''
new = '''            filter_fn=filter_fn,
        )
        # OpenWeights: put a QAD Q4_0 GGUF's own codes and scales into the int4 tensors.
        if os.environ.get("OW_Q40_GGUF"):
            import ow_q40

            ow_q40.apply(model, os.environ["OW_Q40_GGUF"])
        # TODO: deal with checkpoint / computation dtype decoupling.'''
assert s.count(old) == 1, s.count(old)
s = s.replace(old, new)
if not s.startswith('import os') and '\nimport os\n' not in s:
    s = 'import os\n' + s
open(p, 'w').write(s)
print('quantize.py patched')
