# Spot Tracking

<!-- badges: start -->
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Twitter](https://img.shields.io/twitter/follow/Icy_BioImaging?style=social)](https://twitter.com/Icy_BioImaging)
[![Image.sc forum](https://img.shields.io/badge/discourse-forum-brightgreen.svg?style=flat)](https://forum.image.sc/tag/icy)
<!-- badges: end -->

This is the repository for the source code of *Spot Tracking*, a plugin for the [bioimage analysis software Icy](http://icy.bioimageanalysis.org/), which was developed by members or former members of the [Biological Image Analysis unit at Institut Pasteur](https://research.pasteur.fr/en/team/bioimage-analysis/). This plugin is licensed under GPL3 license.     
Icy is developed and maintained by [Biological Image Analysis unit at Institut Pasteur](https://research.pasteur.fr/en/team/bioimage-analysis/). The [source code of Icy](https://gitlab.pasteur.fr/bia/icy) is also licensed under a GPL3 license.     



## Plugin description

<!-- Short description of goals of package, with descriptive links to the documentation website --> 

This plugin ships automated methods for extracting trajectories of multiples objects in a sequence of 2D or 3D images. Up to version 2 it was known as the 'Probabilistic particle tracker' plugin.

A number of precious features for object tracking in microscopy images are embedded: the number of targets can vary through time (objects can appear and disappear), false detection (not originating from a target) are automatically detected and discarded, different target dynamics are available (diffusive, directed movement, or both).
The tracking methods relies on a published method (Chenouard et al., TPAMI, 2013) which is termed ‘multiframe’ in the sense that at a given time point of the image sequence multiple past and futures frames are considered for building the best set of tracks. It is part of the family of ‘probabilistic’ tracking methods in the sense that a statistical model of the particle trajectories and acquisition device is built such as tracking decisions are optimal according to a given statistical criterion with respect to this model. By default, a rough estimation of the parameters of the statistical model will be evaluated by the software such that virtually no parameter tuning is required from the user. However, for optimal control of the methods, a dedicated interface can give access to the full set of parameters.
The tracking methods are freely provided for use and modification. By using the provided methods you agree to properly reference the scientific work at their roots in any written or oral communication exposing a work that took advantage of them. The exact reference is: Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin, "Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013
Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865        
A more detailed user documentation can be found on the Spot Tracking documentation page on the Icy website: http://icy.bioimageanalysis.org/plugin/spot-tracking/               


## Installation instructions

For end-users, refer to the documentation on the Icy website on [how to install an Icy plugin](http://icy.bioimageanalysis.org/tutorial/how-to-install-an-icy-plugin/).      

For developers, see our [Contributing guidelines](https://gitlab.pasteur.fr/bia/icy/-/blob/master/CONTRIBUTING.md) and [Code of Conduct](https://gitlab.pasteur.fr/bia/icy/-/blob/master/CODE-OF-CONDUCT.md).      

<!--  Here we should have some explanations on how to fork this repo (for an example see https://gitlab.pasteur.fr/bia/wellPlateReader). Add any info related to Maven etc. How the project is build (for an example see https://gitlab.pasteur.fr/bia/wellPlateReader). Any additional setup required (authentication tokens, etc).  -->


## Main functions and usage

<!-- list main functions, explain architecture, classname, give info on how to get started with the plugin. If applicable, how the package compares to other similar packages and/or how it relates to other packages -->

Classname: `plugins.nchenouard.particletracking.SpotTrackingPlugin`



## Citation 

Please cite this plugins as follows: Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
"Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013
Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865             


Please also cite the Icy software and mention the version of Icy you used (bottom right corner of the GUI or first lines of the Output tab):     
de Chaumont, F. et al. (2012) Icy: an open bioimage informatics platform for extended reproducible research, [Nature Methods](https://www.nature.com/articles/nmeth.2075), 9, pp. 690-696       
http://icy.bioimageanalysis.org    



## Author(s)      

Nicolas Chenouard


## Additional information

This Java library is a particle tracking plugin for ICY that uses the multiple hypothesis tracking algorithm described in:
Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
"Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013
Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865

ICY webpage: http://icy.bioimageanalysis.org/plugin/Spot_Tracking

The main class is plugins.nchenouard.particletracking.SpotTrackingPlugin.java
and the tracker implementation is in plugins.nchenouard.particletracking.MHTracker

The tracking methods are freely provided for use and modification.
By using the provided methods you agree to properly reference the scientific work at their roots in any written or oral communication exposing a work that took advantage of them.

The library is taking advantage of:
- lpsolve 55 library: lpsolve.sourceforge.net/5.5/‎
- the simplex algorithm found at: algs4.cs.princeton.edu/65reductions/Simplex.java.html‎


