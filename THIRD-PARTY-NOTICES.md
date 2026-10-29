# Third-party notices

PhotoSlide is licensed under the GNU General Public License v3 (see LICENSE). It contains code
derived from the following projects, which are used under their own licenses. The libraries
PhotoSlide depends on (JavaFX, ControlsFX, LibRaw/LibRawFX, Jackson, ...) are not part of this
source tree; their licenses are included in their own packages.

## LightZone

The following parts are ports of algorithms and data from LightZone
(https://github.com/ktgw0316/LightZone). Each of these files names the LightZone classes it is
based on.

- the editing tools in `PhotoSlide/src/main/java/org/photoslide/imageops/lz/`
- the blend modes: `imageops/BlendMode.java`, `imageops/BlendedFilter16.java`
- the masks: `imageops/mask/ColorSelection.java`, `imageops/mask/Region.java`
- the styles: `imageops/Styles.java` (values of LightZone's style templates)

```
Copyright (c) 2005-2011, Fabio Riccardi
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name "LightZone" nor the names of its contributors may be
      used to endorse or promote products derived from this software without 
      specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## JH Labs image filters

`PhotoSlide/src/main/java/org/photoslide/imageops/ImageMath.java` and
`imageops/PixelUtils.java` are from the JH Labs Java image filters by Jerry Huxtable
(http://www.jhlabs.com/ip/filters/).

```
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

The full text of the Apache License 2.0 is in `LICENSE-APACHE-2.0.txt`.

## Geocalc

The GPS classes `BoundingArea`, `Coordinate`, `DMSCoordinate`, `DegreeCoordinate`, `EarthCalc`,
`GPSCoordinate`, `Point` and `RadianCoordinate` in
`PhotoSlide/src/main/java/org/photoslide/browsermetadata/` are from Geocalc
(https://github.com/grumlimited/geocalc).

```
BSD 3-Clause License

Copyright (c) 2018, Grum Ltd (Romain Gallet)
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

Neither the name of Geocalc nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## TwelveMonkeys

`GenericWritableRaster`, `MappedFileBuffer` and `MappedImageFactory` in
`PhotoSlide/src/main/java/org/photoslide/datamodel/customformats/tiffsupport/` and
`datamodel/customformats/webpsupport/` are from TwelveMonkeys ImageIO
(https://github.com/haraldk/TwelveMonkeys).

```
Copyright (c) 2010, Harald Kuhr
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name "TwelveMonkeys" nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## OpenSimplex Noise

`PhotoSlide/src/main/java/org/photoslide/imageops/lz/OpenSimplexNoise.java` is OpenSimplex Noise in
Java by Kurt Spencer (https://gist.github.com/KdotJPG/b1270127455a94ac5d19), released into the
public domain under the Unlicense (full text in the file header).

## ColorChecker reference values

The reference colours of the 24 patches in `editpipeline/ColorCalibration.java` are the CIE Lab
values published by X-Rite for the ColorChecker Classic (November 2014). ColorChecker is a
trademark of X-Rite / Calibrite; PhotoSlide is not affiliated with them.
