[![Build Status](https://travis-ci.org/bloomreach-forge/hst-content-version-utils.svg?branch=develop)](https://travis-ci.org/bloomreach-forge/hst-content-version-utils)

# HST Content Version Utils

This project provides utilities to retrieve versioned, frozen node content using HST Content Beans.

# Documentation (Local)

The documentation can generated locally by this command:

```bash
$ mvn clean install
$ mvn clean site
```

The output is in the ```target/site/``` directory by default. You can open ```target/site/index.html``` in a browser.

# Documentation (GitHub Pages)

Documentation is available at [https://bloomreach-forge.github.io/content-export-import/](https://bloomreach-forge.github.io/content-export-import/).

You can generate the GitHub pages only from ```master``` branch by this command:

```bash
$ mvn clean install
$ find docs -name "*.html" -exec rm {} \;
$ mvn -Pgithub.pages clean site
```

The output is in the ```docs/``` directory by default. You can open ```docs/index.html``` in a browser.

You can push it and GitHub Pages will be served for the site automatically.
