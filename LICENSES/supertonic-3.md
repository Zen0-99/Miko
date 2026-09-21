# Supertonic-3 — OpenRAIL-M

The Supertonic-3 TTS model is published by **Supertone** on Hugging Face
under the **OpenRAIL-M** license:

- Model card + full license text: https://huggingface.co/Supertone/supertonic-3

Miko downloads the **int8-quantized** weights on demand from the
sherpa-onnx `tts-models` release
(`k2-fsa/sherpa-onnx` — https://github.com/k2-fsa/sherpa-onnx/releases);
the repackaged weights remain under the original OpenRAIL-M license
(use restrictions apply — see the model card for the full text). The ten
bundled voice styles (F1–F5, M1–M5) ship inside the model's `voice.bin`
tensor stack and are covered by the same license.

Inference runs on Microsoft's `onnxruntime-android` (MIT), already
attributed under the Pocket TTS entry in About → Licenses.
