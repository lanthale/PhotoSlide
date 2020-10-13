# PhotoSlide
This is application uses concepts from Lightzone and Lightroom and represents a simple Photo management application with a modern and reactive user interface written in JavaFX.

Actually it is only a picture viewer / video file viewer with some small editing features and metadata support.

Stay tuned until the other management features will be released and the edit modul from LightZone is ported over.

The main focus actually is to create better managment support like Lightroom has and not to create another photo editor.

- Why create another photo management/editor ?
I was a happy Lightroom user before Adobe was changing to the Abo model for payments and additionally an update on Apples side (OSX 10.15 dropped support for 32bit) produces issues on Lightroom (could not find any more pictures on network drives, import only partly working, ...) which as read on the internet can only be fixed by a new version which means paying monthly to Adobe.

So I decided to search for an replacement for Lightroom and tried: darktable, ON1, LightZone, digicam and a few others.
All of the listed applications had the issue that they could not handle photo's stored on a NAS. They are working only good if the photos are stored on an external USB drive but cannot handle the delay from the network drive. Additionally all of them have not implemented the delay of a network so that you are getting on each photo a spinning beach ball on OSX for at least 15 to 20 secounds. With Lighroom I do not had such issues and then I tried how Javafx and Java can handle that. Supprisingly the speed was extremly well and much better than in all of the applications listed above including Lightroom.

During my vacation time in spring I could not travel and therefore I decided to start creating this software. It is far from perfect, but it could handle some of my workflow use cases already.

## Screenshot of the application
![PhotoSlide Screenshot](/PhotoSlide-Shot1.png)

## Download of installers including the binary:
https://github.com/lanthale/PhotoSlide/releases/tag/v0.3
Remarks: Linux is not tested yet

## Features
- Viewing of JPG/TIFF/PNG images
- Viewing of movies with MP4/h264 codec
- Lossles editing: Rotated / Crop images / rate images
- Add keywords and tags to images
- Read all metadata (IPTC, XMP, ...) from the image
- Write metadata
- Apply mass updates to metadata on images
- Export images including changes to JPG/TIFF/PNG
- Drag and drop mediafiles to desktop (multi selection on OSX not possible)
- Copy/Paste images from one directory/collection to another
- Have as many places to store photos/videos as you want
- Usage of multi threads for reading image information / editing and metadata support

## Missing features
- View of all raw data format (using libdcraw)
- Implement stacking of photos
- Implement search function
- Implement face recognizion
- Implement GPS including a map view
- Implement multi threading for loading images (actually only one image after the other is loaded)
- ~~Replace icons with iKonli icons~~
- Implement Cut/copy/paste in the collections module so that you can have your actully working collection locally and the rest on the NAS
- Porting the photo edit capabilities over from LightZone (edit module is not implemented yet)
