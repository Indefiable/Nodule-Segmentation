---
title: 'Nodule Analysis: A Fiji plugin for nodule data collection'
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
---

# Summary

When performing analysis on hundreds of plants of root systems that have been 
imaged, it is useful to have a high throughput method for gathering the data
from the plants. Nodule Analysis is a set of two Fiji [@Fiji] (ImageJ2) plugins (Nodule Segmentation
and Nodule Distances) that provides a method for computing the counts, areas, and pair-wise distances along 
the root system of all of the nodules on an image of a root system using various data gathering methods. 
The plugin utilizes part of Weka's FIJI plugin[@Weka], particularly their unsupervised color clustering method to segment the nodules and 
the root system separately. 


# Statement of need

There are currently no unsupervised methods that automates the collection
of nodule counts,sizes, and distances between nodules along the root system.
Root Painter is a program that utilizes deep learning to perform segmentation of a 
variety of biological images[@RootPainter]. Root Painter has been shown to count nodules
effectively, with relatively small error( $$R^2 = .69$$), but it does not consistently
fully outline the nodules, and does not compute their area, only the counts. Additionally,
it utilizes machine learning to improve the output of the data. Our method utilizes 
an unsupervised method for segmentation with some automated post-processing methods
to improve the segmentation, and uses the segmentation to compute the area as opposed to 
just the counts. The user is still granted an opportunity to make changes to the output of the data
before the plugin saves the results. 
Additionally, to our knowledge there is not currently a plugin that takes images of plants and 
compute the distances between marked objects on the plant along the plant's branches.


# Acknowledgements

This project was funded by Porter Labs from Washington State University and 
the development was aided by Niall Miller and Brianna Banting.

# References