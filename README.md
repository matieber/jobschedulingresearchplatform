# LiveDewStream
## The first real stream processing platform for executing deep learning on smartphone clusters at the edge, ever

Powered by [<img src="https://www.python.org/static/img/python-logo@2x.png" alt="Python" style="width:25%; height: 25%;" />](https://www.python.org) [<img src="https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Android_logo_2019_%28stacked%29.svg/1200px-Android_logo_2019_%28stacked%29.svg.png" alt="Android" style="height: 7%; width: 7%;" />](https://www.android.com)

Dew Computing, an evolution of Fog Computing, aims at fulfilling computing needs -e.g. deep learning applied to object classification- close to where data is originated and using computing resources that include consumer electronics devices such as smartphones. Simulation tools like DewSim aid the study of resource allocation mechanisms for exploiting clusters of smartphones, however, there is a gap w.r.t software tools that allow to perform similar studies over real Dew computing testbeds. We have developed LiveDewStream, an open source project to model executable tasks derived from data streams to be run on real smartphone clusters. The project offers a key and missing functionality: reproducibility of battery-driven Dew experiments.

## Features

- Python server to coordinate tensorflow jobs on attached smartphones
- Software-managed energy supply for cluster smartphones
- Android application to coordinate intra-smartphone job execution and smartphone-server communication
- Configurable client-side Python module to submit stream processing experiments to a smartphone cluster
- Python-based framework with abstractions to specialize entities such as stream source and job schedulers

## Technical details

LiveDewStream uses a number of open source projects and Python/OS packages to work properly:

- [Tensorflow Lite](https://www.tensorflow.org/lite) - Library for deploying machine learning models on mobile, microcontrollers and other edge devices
- [pyserial](https://pypi.org/project/pyserial/) - Python Serial Port Extension for Win32, OSX, Linux, BSD, Jython, IronPython
- [web.py](https://webpy.org/) - Simple and powerful Web framework for Python
- [requests](https://docs.python-requests.org) - Elegant and simple HTTP library for Python
- [python3-tk](https://docs.python.org/3/library/tkinter.html) - Python interface to Tcl/Tk
- [importlib](https://docs.python.org/3/library/importlib.html) - Library to create custom Python importers 
- [functools](https://docs.python.org/3/library/functools.html) - Python module for working with high-order functions and operations
- [jq](https://stedolan.github.io/jq/) - Lightweight and flexible command-line JSON processor
- [adb](https://developer.android.com/studio/command-line/adb) - Versatile command-line tool to communicate with/control Android-powered devices
- [net-tools](https://sourceforge.net/projects/net-tools/) - The collection of base networking utilities for Linux
- [aapt](https://developer.android.com/studio/command-line/aapt2) - The base builder for Android applications
- [curl](https://curl.se/) - Command line tool and library for transferring data with URLs

And of course LiveDewStream itself is open source under GNU GPL with a [public repository](http://github.com/matieber/livedewstream) on GitHub.

## Installation

LiveDewSim requires to install a server component, which accepts machine learning jobs and distributes them to attached smartphones, and a job submitter component, which instantiates image streams and submit jobs to the server accordingly.

Both components require [Python](https://www.python.org/) v3.7+ to run.

First off, install Python and pip3 on a Linux OS. Then, install required Python packages:

```sh
pip3 install pyserial web.py requests
```

Moreover, install via the OS package manager the required system packages: python3-tk, jq, adb, net-tools, aapt, and curl. 

The current user must be member of the "dialout" group. From a terminal, type:

```sh
sudo addgroup $USER dialout
```

To run the server, just type:

```sh
cd src/emanager_server
./launch_emanager_server.sh
```

You can optionally edit serverConfig.json properly before running the server.

By default, the Python-based server application will log output to ./log.txt. To include the output of the various satellite OS scripts, you might want to use instead: 

```sh
stdbuf -oL ./launch_emanager_server.sh &> log.txt
```

You might need to (re)build the Android app first (Normapp), which is located under src/emanager_server/Normapp. Please open the Android app project folder using [Android Studio](https://developer.android.com/studio).

Please also bear in mind that lock screen behavior should be disabled in the participating mobile devices.

## Run a dew computing scenario

Once the server is started and mobile devices are attached/registered by the server, the next step is to execute a dew computing scenario. Find an example of a dew computing scenario descriptor at /doc/cs402_scn002.json.
To proceed with the execution, open a terminal, navigate to the root of scnrunner directory and call the run.sh script with the path to the directory containing the scenario descriptor:

```sh
cd src/scnrunner
./run.sh ../doc/
```

## Development

Want to contribute? Great! Do not hesitate to [contact us](mailto:matias.hirsch@isistan.unicen.edu.ar)

## License

GNU GPL
