# PhotoSlide
This is application uses concepts from Lightzone and Lightroom and represents a simple Photo management application with a modern and reactive user interface written in JavaFX.

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=CXWX6CAQ5MMV4)

Actually it is only a picture viewer / video file viewer with some small editing features and metadata support.

Stay tuned until the other management features will be released and the edit modul from LightZone is ported over.

The main focus actually is to create better managment support like Lightroom has and not to create another photo editor.

- Why create another photo management/editor ?
I was a happy Lightroom user before Adobe was changing to the Abo model for payments and additionally an update on Apples side (OSX 10.15 dropped support for 32bit) produces issues on Lightroom (could not find any more pictures on network drives, import only partly working, ...) which as read on the internet can only be fixed by a new version which means paying monthly to Adobe.

So I decided to search for an replacement for Lightroom and tried: darktable, ON1, LightZone, digicam and a few others.
All of the listed applications had the issue that they could not handle photo's stored on a NAS. They are working only good if the photos are stored on an external USB drive but cannot handle the delay from the network drive. Additionally all of them have not implemented the delay of a network so that you are getting on each photo a spinning beach ball on OSX for at least 15 to 20 secounds. With Lighroom I do not had such issues and then I tried how Javafx and Java can handle that. Suprisingly the speed was extremly well and much better than in all of the applications listed above including Lightroom.

During my vacation time in spring I could not travel and therefore I decided to start creating this software. It is far from perfect, but it could handle some of my workflow use cases already.

## System requirements
- Windows 10 or later
- OSX 11.0 or later
- Linux Debian 10 or later/Ubuntu 18.x or later
- 4 GB ram (16 GB for working with RAW files recommended)
- Diskspace: Almost nothing: App around 240MB, Userdirectory: 200 MB (depending on the search DB, but only text is stored in it, no media files)

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
- OSX: Goto in the download folder in Finder and right click on the installer file and click "Open". 
On OSX 11.x open the app and afterwards goto system settings - general tab in the Security & Privacy pane to instruct macOS to ignore its lack of notarization - click on "open anyway" and you have to execute `sudo xattr -r -d com.apple.quarantine /Applications/Photoslide.app`
- Linux: 
  - Download the deb package
  - Run sudo dpkg -i photoslide_1.0.0-1_amd64.deb
  - Click on the icon or run /opt/photoslide/bin/PhotoSlide

## Features
- Viewing of JPG/TIFF/PNG/PSD/WEBP/HEIF images
- Viewing of RAW files for Canon/Nikon/Fuji/Leica/Sigma/Adobe DNG
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
- Printing support inclusive borderless printing (experimental)
- Bookmark files independend where found
- Export/clipboard bookmarked files
- Software update: Check for new version of photoslide and download/install it if user wants it
- View of GPS position information on a OSM map


## Missing features
- Implement face recognizion
- Map view
- Porting the photo edit capabilities over from LightZone (edit module is now implemented but only basic algorithms are now ported over)


## Steps to create your own build
- Install AdoptOpenJDK 21 or later (you can also use openjdk/Azul/coretto/...). Faster startuptime of the app(40%) is only possible if you use JDK 21 (usage of ZGC GC) or later.
- Set env var JAVA_HOME
- Install maven
- On Windows install WiX 3.xx tools, On OSX install XCode including CMD tools
- Run maven with: mvn clean install -Ppackage -f PhotoSlide/pom.xml

If you are stuck at JDK 11 than remove in pom.xml the dependency for librawfx/libheiffx and comment in App.java out the line `RAWImageLoaderFactory.install();` and you are ready to build it under JDK11.

The maven file is downloading every dependency automatically in the background.
