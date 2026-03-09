![Version 4.0.0 alpha 5](https://img.shields.io/badge/v4.0.0-alpha_5-royalblue)
![Java 17+](https://img.shields.io/badge/OpenJDK-17+-5382A1?labelColor=E76F00)
[![License: LGPL v3](https://img.shields.io/badge/LGPLv3-008033?logo=GPLv3&logoSize=auto)](https://www.gnu.org/licenses/lgpl-3.0)
[![Twitter](https://img.shields.io/twitter/follow/Icy_BioImaging)](https://x.com/Icy_BioImaging)
[![Image.sc forum](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fforum.image.sc%2Ftag%2Ficy.json&query=%24.topic_list.tags.0.topic_count&suffix=%20topics&label=forum&color=0AA0B4)](https://forum.image.sc/tag/icy)

# Spot Tracking

This is the repository for the source code of *Spot Tracking*, an extension for the [bioimage analysis software Icy](https://icy.bioimageanalysis.org/), which was developed by members or former members of the [Biological Image Analysis unit at Institut Pasteur](https://research.pasteur.fr/en/team/bioimage-analysis/). This plugin is licensed under the LGPL3 license.     
Icy is developed and maintained by [Biological Image Analysis unit at Institut Pasteur](https://research.pasteur.fr/en/team/bioimage-analysis/). The [source code of Icy](https://gitlab.pasteur.fr/bia/icy/icy) is also licensed under a LGPL3 license.

## Extension description

This extension ships automated methods for extracting trajectories of multiple objects in a sequence of 2D or 3D images.
Up to version 2 it was known as the 'Probabilistic particle tracker' plugin.
<br>
A number of precious features for object tracking in microscopy images are embedded:
the number of targets can vary through time (objects can appear and disappear),
false detections (not originating from a target) are automatically detected and discarded,
different target dynamics are available (diffusive, directed movement, or both).
<br>
The tracking methods rely on a published method (Chenouard et al., TPAMI, 2013) which is termed ‘multiframe’ in the sense that at a given time point of the image sequence multiple past and futures frames are considered for building the best set of tracks.
It is part of the family of ‘probabilistic’ tracking methods in the sense that a statistical model of the particle trajectories and acquisition device is built such as tracking decisions are optimal according to a given statistical criterion with respect to this model.
By default, the software will evaluate an estimation of the statistical model parameters such that virtually no parameter tuning is required from the user.
However, for optimal control of the methods, a dedicated interface can give access to the full set of parameters.
<br>
The tracking methods are freely provided for use and modification. By using the provided methods you agree to properly reference the scientific work at their roots in any written or oral communication exposing a work that took advantage of them. The exact reference is: Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin, "Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736–3750, Nov., 2013
<br>
Pubmed link: https://pubmed.ncbi.nlm.nih.gov/24051732/
<br>
More detailed user documentation can be found on the Spot Tracking documentation page on the Icy website: https://icy.bioimageanalysis.org/plugin/spot-tracking/

## Installation instructions

### Install from Icy website

*Work In Progress*

### Install from source code

#### Requirements
- JDK 17+
- Maven 3.6.3+

#### Steps
1. Clone from git repository using `git clone` command
2. Open your terminal and navigate to the folder containing the cloned repository
3. Run `mvn` command
4. The extension was automatically installed in your local maven repository `~/.m2/repository/`, and registered in your Icy workspace

## Import in your extension

Add this dependency block in your `pom.xml`
```xml
<dependency>
    <groupId>org.bioimageanalysis.icy</groupId>
    <artifactId>spot-tracking</artifactId>
</dependency>
```

## Citation

Please cite this extension as follows: Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
"Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736–3750, Nov., 2013
Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865

Please also cite the Icy software and mention the version of Icy you used (bottom right corner of the GUI or first lines of the Output tab):     
de Chaumont, F. et al. (2012) Icy: an open bioimage informatics platform for extended reproducible research, [Nature Methods](https://www.nature.com/articles/nmeth.2075), 9, pp. 690–696       
https://icy.bioimageanalysis.org

## Author(s)

- Nicolas Chenouard
- Stéphane Dallongeville
- Thomas Musset


## Additional information

This Java library is a particle tracking plugin for ICY that uses the multiple hypothesis tracking algorithm described in:
Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
"Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736–3750, Nov., 2013
Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865

ICY webpage: http://icy.bioimageanalysis.org/plugin/Spot_Tracking

The main class is plugins.nchenouard.particletracking.SpotTrackingPlugin.java
and the tracker implementation is in plugins.nchenouard.particletracking.MHTracker

The tracking methods are freely provided for use and modification.
By using the provided methods you agree to properly reference the scientific work at their roots in any written or oral communication exposing a work that took advantage of them.

The library is taking advantage of:
- lpsolve 55 library: lpsolve.sourceforge.net/5.5/
- the simplex algorithm found at: algs4.cs.princeton.edu/65reductions/Simplex.java.html