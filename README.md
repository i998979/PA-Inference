# PA Inference
PA Inference is an Android app that provides a UI for audio inference. By project [gpt-sovits-onnx-rs](https://github.com/null-define/gpt-sovits-onnx-rs), inferring on mobile device is made possible. By providing trained models and reference audios, audio will be inferred using the sounds of trained model.

With its tri-lingual design, text-to-speech conversion can be easily completed by just providing the text to be converted, then click the language to infer.

### User Interface
<img src="https://github.com/user-attachments/assets/090c7ff0-338b-431c-9575-50f86d312570" width="200">

# Prerequisites
- Converted GPT-SoVITS v2 models in .onnx format as described in [gpt-sovits-onnx-rs](https://github.com/null-define/gpt-sovits-onnx-rs)
- 3-10s reference audio in .wav format

# Installation
- Download/compile and install the app
- Create a folder containing the following 3 folders
  - yue
  - zh
  - en
- Put converted models and required models in the folders accordingly
- Put reference audios in the folders accordingly, naming it `ref.wav`

# Usage
- Select the root folder containing `yue`, `zh`, and `en`
- Modify the reference text which matches the ref.wav
- Enter the text to infer
- Press the language to infer

Audio will be played and shown in Inference Results. By clicking `➦`, audio can be shared. By clicking `✖`, audio will be deleted.



## Terms of Use
- You are allowed to download the source code, compile and install on your own device.
- You are not allowed to redistribute any part of the code and claim that is your work.
