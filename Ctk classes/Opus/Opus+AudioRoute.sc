+ Opus {

	loadAudioRoutes {arg force = false;
		this.prLoadAudioInRoutes(force);
		this.prLoadAudioOutRoutes(force);
	}

	inputForKey {arg key;
		var opusInputs, foundInput;
		opusInputs = this.prGetInputs;
		foundInput = opusInputs[key];
		foundInput.isNil.if({
			("Input for \\" ++ key ++ " not found").warn;
		});
		^foundInput;
	}

	addInputForKey {arg key, audioBus;
		var opusInputs;
		opusInputs = this.prGetInputs;
		opusInputs.add(key -> audioBus);
	}

	prLoadAudioInRoutes {arg force = false;
		var inputs;
		inputs = thisProcess.interpreter.executeFile(audioInPath.absolutePath);
		inputs.notNil.if({
			var opusInputs;
			opusInputs = this.prGetInputs;
			inputs.do({arg inputArray;
				var key, idx, numChannels, audioBus;
				#key, idx, numChannels = inputArray;
				audioBus = CtkAudio.play(numChannels, idx + this.server.options.numOutputBusChannels, this.server);
				this.addInputForKey(key, audioBus);
			})
		}, {
			force.if({
				var pathName;
				pathName = this.createTemplateAudioInRoute(force);
				pathName.notNil.if({
					"Created a template audio input file..".postln;
					this.prLoadAudioInRoutes(false);
				});
			}, {
				"No audio inputs have been loaded".warn;
			})
		})
	}

	outputForKey {arg key;
		var opusOutputs, foundOutput;
		opusOutputs = this.prGetOutputs;
		foundOutput = opusOutputs[key];
		foundOutput.isNil.if({
			("Output for \\" ++ key ++ " not found").warn;
		});
		^foundOutput;
	}

	addOutputForKey {arg key, audioBus;
		var opusOutputs;
		opusOutputs = this.prGetOutputs;
		opusOutputs.add(key -> audioBus);
	}

	prLoadAudioOutRoutes {arg force = false;
		var outputs;
		outputs = thisProcess.interpreter.executeFile(audioOutPath.absolutePath);
		outputs.notNil.if({
			var opusOutputs;
			opusOutputs = this.prGetOutputs;
			outputs.do({arg outputArray;
				var key, idx, numChannels, audioBus;
				#key, idx, numChannels = outputArray;
				audioBus = CtkAudio.play(numChannels, idx, this.server);
				this.addOutputForKey(key, audioBus);
			})
		}, {
			force.if({
				var pathName;
				pathName = this.createTemplateAudioOutRoute(force);
				pathName.notNil.if({
					"Created a template audio input file..".postln;
					this.prLoadAudioOutRoutes(false);
				});
			}, {
				"No audio inputs have been loaded".warn;
			})
		})
	}


	createTemplateAudioInRoute {arg force = false;
		var fileStr, file, filePath, pathName;
		fileStr = "/*
A sample set of audio inputs. This should be an array of arrays. Each secondary array should contain a key to access the input with, and a 0 based index for a channel on the physical device followed by the number of channels.
*/
[
    [\\myInputOne, 0, 1],
    [\\myInputTwo, 1, 1],
];";
		filePath = this.audioInPath.absolutePath;
		^this.prCreateTemplateWithStringAtPath("inputs", filePath, fileStr, force);
	}

	createTemplateAudioOutRoute {arg force = false;
		var fileStr, file, filePath, pathName;
		fileStr = "/*
A sample set of audio outputs. This should be an array of arrays. Each secondary array should contain a key to access the output with, and a 0 based index for a channel on the physical device followed by the number of channels.
*/
[
    [\\myStereoOutput, 0, 2],
    [\\myStereoMonitorOutput, 2, 1],
];";
		filePath = this.audioOutPath.absolutePath;

		^this.prCreateTemplateWithStringAtPath("outputs", filePath, fileStr, force);
	}
}