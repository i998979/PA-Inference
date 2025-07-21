# PA Inference
PA Inference is an Android app that provides a UI for audio inference. By providing trained models and reference audios, audio will be inferred using the sounds of trained model.

With its tri-lingual design, text-to-speech conversion can be easily completed by just providing the text to be converted.

### User Interface
<img src="https://github.com/user-attachments/assets/090c7ff0-338b-431c-9575-50f86d312570" width="200">

# Prerequisites
- Converted GPT-SoVITS models in .onnx format as described in https://github.com/null-define/gpt-sovits-onnx-rs
- Reference audio in .wav format

# Installation
- Download/compile and install the application
- Create a folder containing the following 3 folders
  - yue
  - zh
  - en
- Put converted models and required models in the folders accordingly
- Put reference audio in .wav in the folders accordingly, naming it `ref.wav`

# Usage
- Select the root folder containing `yue`, `zh`, and `en`
- Modify the reference text which matches the ref.wav
- Enter the text to infer
- Press the language to infer

Audio will be played and shown in Inference Results. By clicking `➦`, audio can be shared. By clicking `✖`, audio will be deleted.



## Terms of Use
- You are allowed to download the source code, compile and install on your own device.
- You are not allowed to redistribute any part of the code and claim that is your work.
