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

Screenshot of the fulltext search feature:
![PhotoSlide Screenshot](/PhotoSlide-Shot2.png)

Screenshot of the editor feature:
![PhotoSlide Screenshot](/PhotoSlide-Shot3.png)

## Download of installers:
https://github.com/lanthale/PhotoSlide/releases/latest
The software is not notarized with developer accounts on OSX/Windows.

- Windows: Execute the installer. If defender is poping up please click on "more info" and then on "run anyway"
- OSX: Execute the installer. The first time right click on the icon in Application folder and select "open"
On OSX 11.x open the app and afterwards goto system settings - general tab in the Security & Privacy pane to instruct macOS to ignore its lack of notarization - click on "open anyway"
- Linux: 
  - Download the deb package
  - Run sudo dpkg -i photoslide_1.0.0-1_amd64.deb
  - Click on the icon or run /opt/photoslide/bin/PhotoSlide

## Features
- Viewing of JPG/TIFF/PNG/PSD images
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
- Nicer icons with (iKonli)
- Cut/copy/paste in the collections module so that you can have your actully working collection locally and the rest on the NAS
- Stacking of photos
- Full text search using a H2 database (will be created automatically in the background)
- Implemented multithreading for media loading (depends on the operating system - try to create threads as much as possible from the OS)
- First implementation of an edit view (actually only an exposure filter is added)
- Support for GPS information in meta data of image files
- Printing support inclusive borderless printing (experimntal)


## Missing features
- ~~View of all raw data format (using libdcraw)
- ~~Implement stacking of photos~~
- ~~Implement search function~~
- ~~Implement multi threading for loading images (actually only one image after the other is loaded)~~
- ~~Replace icons with iKonli icons~~
- ~~Implement Cut/copy/paste in the collections module so that you can have your actully working collection locally and the rest on the NAS~~
- Implement face recognizion
- Map view
- Porting the photo edit capabilities over from LightZone (edit module is not implemented yet)


## Steps to create your own build
- Install AdoptOpenJDK 16 or later (you can also use openjdk/Azul/coretto/...). Faster startuptime of the app(40%) is only possible if you use JDK 14 or later.
- Set env var JAVA_HOME
- Install maven
- On Windows install WiX 3.xx tools, On OSX install XCode including CMD tools
- Run maven with: mvn clean compile package -Ppackage -f PhotoSlide/pom.xml

If you are stuck at JDK 11 than remove in pom.xml the dependency for librawfx and comment in App.java out the line `RAWImageLoaderFactory.install();` and you are ready to build it under JDK11.

The maven file is downloading every dependency automatically in the background.
