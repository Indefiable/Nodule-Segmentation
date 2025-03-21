---
title: 'Gala: A Python package for galactic dynamics'
tags:
  - Java
  - ImageJ
  - FIJI
  - biology
authors:
  - name: Brandin Farris
    orcid: 0009-0002-2462-6094
    equal-contrib: true
    affiliation: "1, 2" # (Multiple affiliations must be quoted)
  - name: Bala Krishanmoorthy
	orcid: 0000-0002-2727-6547
    equal-contrib: false # (This is how you can denote equal contributions between multiple authors)
    affiliation: 2
affiliations:
 - name: Washington State University
   index: 1
 - name: Porter Labs
   index: 2
date: 21 March 2025
bibliography: paper.bib

# Summary

When performing analysis on hundreds of plants of root systems that have been 
imaged, it is useful to have a high throughput method for gathering the data
from the plants. Nodule Analysis is a set of two [Fiji] (ImageJ2) plugins (Nodule Segmentation
and Nodule Distances) that provides a method for computing the counts, areas, and pair-wise distances along 
the root system of all of the nodules on an image of a root system using various data gathering methods. 
The plugin utilizes part of [Weka], particularly their unsupervised color clustering method to segment the nodules and 
the root system separately. 


# Statement of need

There are currently no unsupervised methods that automates the collection
of nodule counts,sizes, and distances between nodules along the root system.
While [reference] can segment nodules in images quite well, it requries training a model on your data set, which
often takes too many images to become sufficiently accurate, particularly when your
data set is less than around 500. Remaining 
Our plugin segments the nodules in the given images, allows the user the option to make
changes, and categorizes the nodules by color. 
Additionally, there is not a plugin that takes images of plants and converts them into 
a graph to compute distances between marked objects on the plant along the plants branches. 


# Citations

Citations to entries in paper.bib should be in
[rMarkdown](http://rmarkdown.rstudio.com/authoring_bibliographies_and_citations.html)
format.

If you want to cite a software repository URL (e.g. something on GitHub without a preferred
citation) then you can do it with the example BibTeX entry below for @fidgit.

For a quick reference, the following citation commands can be used:
- `@author:2001`  ->  "Author et al. (2001)"
- `[@author:2001]` -> "(Author et al., 2001)"
- `[@author1:2001; @author2:2001]` -> "(Author1 et al., 2001; Author2 et al., 2002)"


# Acknowledgements

This project was funded by Porter Labs from Washington State University and 
the development was aided by Niall Miller and Brianna Banting.

# References