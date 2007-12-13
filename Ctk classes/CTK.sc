CtkObj {
	var <objargs;
	
	addTo {arg aCtkScore;
		aCtkScore.add(this);
		^this;
		}
			
	addGetter {arg key, defaultVal;
		objargs.put(key.asSymbol, defaultVal);
		this.addUniqueMethod(key.asSymbol, {arg object; object.objargs[key]});
		}

	addSetter {arg key;
		this.addUniqueMethod((key.asString++"_").asSymbol, 
			{arg object, newval; object.objargs[key] = newval; object;
			});
		}
		
	addMethod {arg key, func;
		objargs.put(key.asSymbol, func);
		this.addUniqueMethod(key.asSymbol, {arg object ... args; 
			objargs[key].value(object, args);
			});
		}
		
	addParameter {arg key, defaultVal;
		defaultVal.isKindOf(Function).if({
			this.addMethod(key, defaultVal);
			}, {
			this.addGetter(key, defaultVal);
			this.addSetter(key);
			})
		^this;
		}
	}

// a wrapper for Score... takes CtkEvents and calcs a pad time, sorts, etc.
CtkScore : CtkObj {
	
	var <endtime = 0, score, <buffers, <ctkevents, <ctkscores, <controls, notes, <others, 
		<buffermsg, buffersScored = false, <groups, oscready = false;
	
	*new {arg ... events;
		^super.new.init(events);
		}

	init {arg events;
		objargs = Dictionary.new;
		score = Score.new;
		ctkscores = Array.new;
		buffers = Array.new;
		buffermsg = Array.new;
		groups = Array.new;
		notes = Array.new;
		ctkevents = Array.new;
		controls = Array.new;
		others = Array.new;
		events.notNil.if({
			this.add(events);
			});
		}
				
	add {arg ... events;
		events.flat.do({arg event;
			case { // if the event is a note ...
				event.isKindOf(CtkNote)
				} {
				notes = notes.add(event);
				(event.releases.size > 0).if({
					event.releases.do({arg me;
						this.add(me.ctkNote);
						})
					});
				this.checkEndTime(event);
				} {
				event.isKindOf(CtkGroup);
				} {
				groups = groups.add(event);
				this.checkEndTime(event);
				} { // if the event is a buffer
				event.isKindOf(CtkBuffer);
				} {
				buffersScored.if({buffersScored = false});
				buffers = buffers.add(event);
				} {
				event.isKindOf(CtkEvent);
				} {
				ctkevents = ctkevents.add(event);
				this.checkEndTime(event);
				} {
				event.isKindOf(CtkControl);
				} {
				controls = controls.add(event);
				event.isScored = true;
				event.ctkNote.notNil.if({
					this.add(event.ctkNote)
					});
				this.checkEndTime(event);
				} {
				event.isKindOf(CtkScore);
				} {
				ctkscores = ctkscores.add(event);
				} {
				event.respondsTo(\messages);
				} {
				others = others.add(event);
				event.respondsTo(\endtime).if({
					this.checkEndTime(event)
					})
				} {
				true
				} {
				"It appears that you are trying to add a non-Ctk object to this score".warn;
				}
			});
			oscready.if({this.clearOSC});
		}
	
	clearOSC {
		oscready = false; score = Score.new;	
		}
		
	checkEndTime {arg event;
		(event.endtime).notNil.if({
			(endtime < event.endtime).if({
				endtime = event.endtime
				})
			});	
	}
	
	notes {arg sort = true;
		oscready.if({this.clearOSC});
		^notes.sort({arg a, b; a.starttime <= b.starttime});
		}
		
	notesAt {arg time, thresh = 0.0001;
		var notelist;
		notelist = this.notes;
		^notelist.select({arg me; me.starttime.fuzzyEqual(time, thresh) > 0})
		}
		
	score {
		^score.score;
		}
		
	saveToFile {arg path;
		this.buildOSCScore;
		this.addBuffers;
		this.sortScore;	
		this.mergeTimes;
		score.add([endtime, 0]);		
		path.notNil.if({score.saveToFile(path)});
		}
	
	sortScore {
		score.add([endtime, [0]]);
		score.sort({arg a, b; a[0] < b[0]});
		}
	
	addBuffers {
		buffersScored.not.if({
			buffers.do({arg me;
				score.add([0.0, me.bundle]);
				score.add([endtime, me.freeBundle]);
				(me.closeBundle.notNil).if({
					score.add([endtime, me.closeBundle]);
					});
				});
			this.addBufferMsg;
			})
		}
		
	addBufferMsg {
		buffers.do({arg me;
			(me.messages.size > 0).if({
				me.messages.do({arg thismsg;
					score.add(thismsg);
					})
				})
			});
		buffersScored = true;	
		}
	
	// put together all NON buffer type events.
	// make sure groups and controls are first
	// Maybe do notes and controls, this.mergeTimes
	// then add groups?
	
	// builds everything except the buffers since they act
	// different in NRT and RT
	
	buildOSCScore {
		oscready.not.if({
			ctkscores.do({arg thisscore;
				this.merge(thisscore);
				});
			ctkevents.do({arg thisctkev;
				thisctkev.score.items.do({arg me;
					this.add(me);
					})
				});
			groups.do({arg thisgroup;
				thisgroup.prBundle.do({arg me;
					score.add(me);
					})
				});
			controls.do({arg thiscontrol;
				thiscontrol.messages.do({arg me;
					score.add(me)
					});
				});
			buffermsg.do({arg thisbuffermsg;
				score.add(thisbuffermsg)
				});
			notes.do({arg thisnote;
				thisnote.prBundle.do({arg me;
					score.add(me);
					});
				});
			oscready = true;
			});
		}
		
	mergeTimes {
		var idx, ascore, blocks, offset;
		idx = 0;
		offset = 1.0e-07;
		ascore = score.score;
		while({
			((ascore[idx][0].fuzzyEqual(ascore[idx+1][0], 0.00001) > 0) and: 
					{ascore[idx].bundleSize < 1024}).if({
				ascore[idx+1].removeAt(0);
				ascore[idx+1].do({arg me;
					((me[0] == \g_new) || (me[0] == "/g_new")).if({
						ascore[idx] = ascore[idx].insert(1, me);
						}, {
						ascore[idx] = ascore[idx].add(me);
						})
					});
				ascore.removeAt(idx+1);
				}, {
				idx = idx + 1;
				});
			idx < (ascore.size-1); 		
			});
		// go through the score again... if bundles have the same time stamps, there can be 
		// problem... add a very small offset (< 0.1 of a sample) to avoid the problem but still
		// give sample accuracy???
		this.sortScore;
		while({
			blocks = 0;
			while({
				ascore[idx][0] == ascore[idx-1-blocks][0];
				}, {
				blocks = blocks + 1;
				});
			(blocks > 0).if({
				ascore[idx][0] = ascore[idx][0] + (offset * blocks)
				});
			idx = idx - 1;
			idx > 1;
			})
		
		}
	
	// create the OSCscore, load buffers, play score
	play {arg server, clock, quant = 0.0;
		server = server ?? {Server.default};
		server.boot;
		server.waitForBoot({
			this.loadBuffers(server, clock, quant);
			CmdPeriod.doOnce({
				(buffers.size > 0).if({
					buffers.do({arg me;
						me.free(nil);
						});
					"Buffers freed".postln;
					})
				})
			})
		}
	
	loadBuffers {arg server, clock, quant;
		var cond;
		cond = Condition.new;
		Routine.run({
			(buffers.size > 0).if({
				server.sync(cond, 
					Array.fill(buffers.size, {arg i;
						buffers[i].bundle;
						})
					);
				buffers.do({arg me;
					score.add([endtime, me.freeBundle]);
					(me.closeBundle.notNil).if({
						score.add([endtime, me.closeBundle]);
						})
					});
				this.addBufferMsg;
				"Buffer loaded!".postln;
				});
			this.buildOSCScore;
			this.sortScore;	
			this.mergeTimes;			
			score.play(server, clock, quant); // finally... play the score!
			});
		}
		
	// SC2 it! create OSCscore, add buffers to the score, write it
	write {arg path, duration, sampleRate = 44100, headerFormat = "AIFF", 
			sampleFormat = "int16", options;
		this.saveToFile;
		score.recordNRT("/tmp/trashme", path, sampleRate: sampleRate, 
			headerFormat: headerFormat,
		 	sampleFormat: sampleFormat, options: options, duration: duration);
		}
		
	// add a time to all times in a CtkScore
	/* will probably have to add events and controls here soon */
	/* returns a NEW score with the events offset */
	
	offset {arg duration;
		var newScore, newNote;
		newScore = CtkScore.new;
		this.items.do({arg me;
			me.isKindOf(CtkNote).if({
				newScore.add(me.copy(me.starttime + duration));
				}, {
				newNote = me.deepCopy;
				newNote.server = me.server; // deepCopy changes the server! This can be bad.
				newNote.starttime_(me.starttime + duration);
				newScore.add(newNote);
				})
			});
		^newScore;
		}
		
	items {^notes ++ groups ++ controls ++ ctkevents ++ buffers ++ buffermsg ++ others }
	
	merge {arg newScore, newScoreOffset = 0;
		var addScore;
		addScore = newScore.offset(newScoreOffset);
		this.add(addScore.items);
		
	}
}
// creates a dictionary of Synthdefs, and CtkNoteObjects
CtkProtoNotes {
	var <synthdefs, <dict;
	*new {arg ... synthdefs;
		^super.newCopyArgs(synthdefs).init;
		}
		
	init {
		dict = Dictionary.new;
		this.addToDict(synthdefs);
		}
	
	// load and add to the dictionary
	addToDict {arg sds;
		sds.do({arg me;
			case
				{me.isKindOf(SynthDef)}
				{dict.add(me.name -> CtkNoteObject.new(me))}
				{me.isKindOf(SynthDescLib)}
				{me.read;
				me.synthDescs.do({arg thissd;
					dict.add(thissd.name -> CtkNoteObject.new(thissd.name.asSymbol))
					});
				}
			})	
		}
	
	at {arg id;
		^dict[id.asString]
		}
		
	add {arg ... newsynthdefs;
		synthdefs = synthdefs ++ newsynthdefs;
		this.addToDict(newsynthdefs);
		}
}
	
		
CtkNoteObject {
	var <synthdef, <server, <synthdefname, args;
	*new {arg synthdef, server;
		^super.newCopyArgs(synthdef, server).init;
		}
		
	init {
		var sargs, sargsdefs, sd, count, tmpar, namesAndPos, sdcontrols, tmpsize;
		case
			{
			synthdef.isKindOf(SynthDef)
			}{
			this.buildControls;
			}{
			// if a string or symbol is passed in, check to see if SynthDescLib.global 
			// has the SynthDef
			(synthdef.isKindOf(String) || synthdef.isKindOf(Symbol) || 
				synthdef.isKindOf(SynthDesc)) 
			}{
			synthdef.isKindOf(SynthDesc).if({
				sd = synthdef;
				}, {	
				sd = SynthDescLib.global.at(synthdef);
				});
			sd.notNil.if({
				// check if this is a SynthDef being read from disk... if it is, it
				// has to be handled differently
				sd.def.allControlNames.notNil.if({
					synthdef = sd.def;
					this.buildControls;
					}, {
					synthdef = sd.def;
					args = Dictionary.new;
					synthdefname = synthdef.name;
					count = 0;
					namesAndPos = [];
					sd.controls.do({arg me, i;
						(me.name != '?').if({
							namesAndPos = namesAndPos.add([me.name, i]);
							}); 
						});
					sdcontrols = namesAndPos.collect({arg me, i;
						(i < (namesAndPos.size - 1)).if({
							tmpsize = namesAndPos[i + 1][1] - me[1];
							[me[0].asSymbol, (tmpsize > 1).if({
								(me[1]..(namesAndPos[i+1][1] - 1)).collect({arg j;
									sd.controls[j].defaultValue;
									})
								}, {
								sd.controls[me[1]].defaultValue;
								})]
							}, {
							tmpsize = sd.controls.size - 1 - me[1];
							[me[0].asSymbol, (tmpsize > 1).if({
								(me[1] .. (sd.controls.size) - 1).collect({arg j;
									sd.controls[j].defaultValue;
									}, {
									sd.controls[me[1]].defaultValue;
									})
								})]
							})
						});
					sdcontrols.do({arg me;
						var name, def;
						#name, def = me;
						args.add(name -> def);
						})
					})
				},{
				"The SynthDef id you requested doesn't appear to be in your global SynthDescLib. Please .memStore your SynthDef, OR run SynthDescLib.global.read to read the SynthDesscs into memory".warn
				})
			}
		}
		
	buildControls {
		synthdef.load(server ?? {Server.default});
		args = Dictionary.new;
		synthdefname = synthdef.name;
		synthdef.allControlNames.do({arg ctl, i;
			var def, name = ctl.name;
			def = ctl.defaultValue ?? {
				(i == (synthdef.allControlNames.size - 1)).if({
					synthdef.controls[ctl.index..synthdef.controls.size-1];
					}, {
					synthdef.controls[ctl.index..synthdef.allControlNames[i+1].index-1];
					})
				};
			args.add(name -> def);
			})	
		}
		
	// create an CtkNote instance
	new {arg starttime = 0.0, duration, addAction = 0, target = 1, server;
		^CtkNote.new(starttime, duration, addAction, target, server, synthdefname)
			.args_(args.deepCopy);
		}
		
	args {
		("Arguments and defaults for SynthDef "++synthdefname.asString++":").postln;
		args.keysValuesDo({arg key, val;
			("\t"++key++" defaults to "++val).postln;
			});
		^args;
		}
	
}

CtkSynthDef : CtkNoteObject {
	*new {arg name, ugenGraphFunc, rates, prependArgs, variants;
		var synthdef;
		synthdef = SynthDef(name, ugenGraphFunc, rates, prependArgs, variants);
		^super.new(synthdef);
		}
	}
	
CtkNode : CtkObj {
	classvar addActions, <nodes, <servers, <resps, cmd, <groups;

	var <addAction, <target, <>server;
	var >node, <>messages, <>starttime, <>willFree = false;
	var <isPaused = false, <releases;

	node {
		^node ?? {node = server.nextNodeID};
		}
		
	watch {arg group;
		var thisidx;
		this.addServer(group);
		thisidx = servers.indexOf(server);
		nodes[thisidx] = nodes[thisidx].add(node);
		group.notNil.if({group.children = group.children.add(node)});
		}
		
	addServer {arg group;
		var idx;
		groups.indexOf(group).isNil.if({
			groups = groups.add(group)
			});
		servers.includes(server).not.if({
			idx = servers.size;
			servers = servers.add(server); // add the server
			nodes = nodes.add([]); // add an array for these nodes to live in
			resps = resps.add(OSCresponderNode(server.addr, '/n_end', {arg time, resp, msg;
				nodes[idx].remove(msg[1]);
				(groups.size > 0).if({
					groups.do({arg me;
						me.notNil.if({
							me.children.remove(msg[1]);
							})
						});
					})
				}).add); // add a responder to remove nodes
			cmd.if({cmd = false; CmdPeriod.doOnce({this.cmdPeriod})});
			});
	
		}
	
	isPlaying {
		var idx;
		this.addServer;
		(servers.size > 0).if({
			idx = servers.indexOf(server);
			(node.notNil && nodes[idx].includes(node)).if({^true}, {^false});
			}, {
			^false
			})
		}
		
	cmdPeriod {
		resps.do({arg me; me.remove});
		resps = [];
		servers = [];
		nodes = [];
		cmd = true;
		}
		
	set {arg time = 0.0, key, value; //, latency = 0.1;
		var bund;
		bund = [\n_set, this.node, key, value];
		this.isPlaying.if({ // if playing... send the set message now!
			SystemClock.sched(time, {
				server.sendBundle(nil, bund);
				});
			}, {
			starttime = starttime ?? {0.0};
			messages = messages.add([starttime + time, bund]);
			})
		}
	
	setn {arg time = 0.0, key ... values;
		var bund;
		values = values.flat;
		bund = [\n_setn, this.node, key, values.size] ++ values;
		this.isPlaying.if({
			SystemClock.sched(time, {server.sendBundle(nil, bund)});
			}, {
			starttime = starttime ? {0.0};
			messages = messages.add([time+starttime, bund])
			})		
		}
	
	release {arg time = 0.0, key = \gate;
		this.set(time, key, 0);
		willFree = true;
		((releases.size > 0) && this.isPlaying).if({
			Routine.run({
				while({
					0.1.wait;
					this.isPlaying.not.if({
						(releases.size > 0).if({
							releases.do({arg me; me.free})
							});
						});
					this.isPlaying;
					})
				});
			});
		^this;
		}
	
	// immeditaely kill the node
	free {arg time = 0.0, addMsg = true; //, latency = 0.1;
		var bund;
		bund = [\n_free, this.node];
		willFree = true;
		this.isPlaying.if({
			SystemClock.sched(time, {
				server.sendBundle(nil, bund);
				(releases.size > 0).if({	
					releases.do({arg me;
						me.free;
						})
					})
				});
			}, {
			addMsg.if({
				messages = messages.add([time+starttime, bund]);
				})
			})
		}
	
	pause {
		this.isPlaying.if({
			isPaused.not.if({
				server.sendMsg(\n_run, node, 0);
				isPaused = true;
				})
			})
		}

	run {
		this.isPlaying.if({
			isPaused.if({
				server.sendMsg(\n_run, node, 1);
				isPaused = false;
				})
			})
		}
					
	asUGenInput {^node}
		
	*initClass {
		addActions = IdentityDictionary[
			\head -> 0,
			\tail -> 1,
			\before -> 2,
			\after -> 3,
			\replace -> 4,
			0 -> 0,
			1 -> 1,
			2 -> 2,
			3 -> 3,
			4 -> 4
			];
		nodes = [];
		servers = [];
		resps = [];
		cmd = true;
		groups = [];
		}
	}	

// these objects are similar to the Node, Synth and Buffer objects, except they are used to 
// create Scores and don't directly send messages to the Server

CtkNote : CtkNode {

	var <duration, <synthdefname,
		<endtime, <args, <setnDict, <mapDict;
			
	*new {arg starttime = 0.0, duration, addAction = 0, target = 1, server, synthdefname;
		server = server ?? {Server.default};
		^super.newCopyArgs(Dictionary.new, addAction, target, server)
			.init(starttime, duration, synthdefname);
		}
		
	copy {arg newStarttime;
		var newNote;
		newStarttime = newStarttime ?? {starttime};
		newNote = this.deepCopy;
		newNote.server_(server);
		newNote.starttime_(newStarttime);
		newNote.messages = Array.new;
		newNote.node_(nil); 
		newNote.args_(args.deepCopy);
		^newNote;
		}

	init {arg argstarttime, argduration, argsynthdefname;
		starttime = argstarttime;
		duration = argduration;
		synthdefname = argsynthdefname;
		node = nil; //server.nextNodeID;
		messages = Array.new;
		(duration.notNil && (duration != inf)).if({
			endtime = starttime + duration;
			});
		setnDict = Dictionary.new;
		mapDict = Dictionary.new;
		releases = [];
		}
	
	starttime_ {arg newstart;
		starttime = newstart;
		(duration.notNil && (duration != inf)).if({
			endtime = starttime + duration;
			})
		}
	
	duration_ {arg newdur;
		duration = newdur;
		starttime.notNil.if({
			endtime = starttime + duration;
			})
		}

	args_ {arg argdict;
		args = argdict;
		argdict.keysValuesDo({arg argname, val;
			this.addUniqueMethod(argname.asSymbol, {arg note; args.at(argname)});
			this.addUniqueMethod((argname.asString++"_").asSymbol, {arg note, newValue;
				var oldval;
				oldval = args[argname];
				args.put(argname.asSymbol, newValue);
				(newValue.isKindOf(CtkControl) and: {
						(newValue.isPlaying || newValue.isScored)}.not).if({
					newValue.starttime_(starttime);
					releases = releases.add(newValue);
					});
				this.isPlaying.if({
					this.handleRealTimeUpdate(argname, newValue, oldval);
					});
				note;
				});
			});		
		}
		
	handleRealTimeUpdate {arg argname, newValue, oldval; //, latency = 0.1;
		case {
			(newValue.isArray || newValue.isKindOf(Env) || newValue.isKindOf(InterplEnv))
			}{
			newValue = newValue.asArray;
			server.sendBundle(nil, [\n_setn, node, argname, newValue.size] ++
				newValue) 
			}{
			newValue.isKindOf(CtkControl)
			}{
			newValue.isPlaying.not.if({
				newValue.play;
				});
			server.sendBundle(nil, [\n_map, node, argname, newValue.bus])
			}{
			true
			}{
			server.sendBundle(nil, [\n_set, node, argname, newValue.asUGenInput])
			};
		// real-time support
		oldval.isKindOf(CtkControl).if({
			releases.indexOf(oldval).notNil.if({
				oldval.free;
				releases.remove(oldval);
				})
			});
		}

	// every one of these has a tag and body... leaves room for addAction and 
	// target in CtkEvent
	
	newBundle {
		var bundlearray, initbundle;
		bundlearray =	this.buildBundle;
		initbundle = [starttime, bundlearray];
		setnDict.keysValuesDo({arg key, val;
			initbundle = initbundle.add([\n_setn, node, key, val.size] ++ val);
			});
		mapDict.keysValuesDo({arg key, val;
			initbundle = initbundle.add([\n_map, node, key, val.asUGenInput])			});
		^initbundle;	
		}
		
	buildBundle {
		var bundlearray;
		(target.isKindOf(CtkNote) || target.isKindOf(CtkGroup)).if({
			target = target.node});
		bundlearray =	[\s_new, synthdefname, this.node, addActions[addAction], target];
		args.keysValuesDo({arg key, val;
			case {
				(val.isArray || val.isKindOf(Env) || val.isKindOf(InterplEnv))
				}{
				setnDict.add(key -> val.asArray)
				}{
				val.isKindOf(CtkControl)
				}{
				mapDict.add(key -> val);
				}{
				true
				}{
				bundlearray = bundlearray ++ [key, val.asUGenInput];
				}
			});
		^bundlearray;		
		}

	bundle {
		var thesemsgs;
		thesemsgs = [];
		thesemsgs = thesemsgs.add(this.newBundle);
		(duration.notNil && willFree.not).if({
			thesemsgs = thesemsgs.add([(starttime + duration).asFloat, [\n_free, node]]);
			});
		^thesemsgs;
		}
			
	// support playing and releasing notes ... not for use with scores
	play {arg latency = 0.1, group;
		var bund, start;
		this.isPlaying.not.if({
			SystemClock.sched(starttime ?? {0.0}, {
				bund = OSCBundle.new;
				bund.add(this.buildBundle);
				setnDict.keysValuesDo({arg key, val;
					bund.add([\n_setn, node, key, val.size] ++ val);
					});
				mapDict.keysValuesDo({arg key, val;
					val.isPlaying.not.if({val.play});
					bund.add([\n_map, node, key, val.asUGenInput]);
					});
				bund.send(server, nil);
				this.watch(group);
				// if a duration is given... kill it
				duration.notNil.if({
					SystemClock.sched(duration, {this.free(0.1, false)})
					});
				});
			^this;
			}, {
			"This instance of CtkNote is already playing".warn;
			})
		}

	prBundle {
		^messages ++ this.bundle;
		}		
	}

/* methods common to CtkGroup and CtkNote need to be put into their own class (CtkNode???) */
CtkGroup : CtkNode {
	var <endtime = nil, <duration, <isGroupPlaying = false, <>children;
	
	*new {arg starttime = 0.0, duration, node, addAction = 0, target = 1, server;
		^super.newCopyArgs(Dictionary.new, addAction, target, server, node)
			.init(starttime, duration);
		}
		
	*play {arg starttime = 0.0, duration, node, addAction = 0, target = 1, server;
		^this.new(starttime, duration, node, addAction, target, server).play;
		}
		
	init {arg argstarttime, argduration;
		starttime = argstarttime;
		duration = argduration;
		duration.notNil.if({
			endtime = starttime + duration
			});
		target = target.asUGenInput;
		server = server ?? {Server.default};
		messages = Array.new;
		children = Array.new;
		}
		
	newBundle {
		var start, bundlearray;
		bundlearray =	this.buildBundle;
		start = starttime ?? {0.0}
		^[starttime, bundlearray];	
		}
	
	buildBundle {
		var bundlearray;
		bundlearray =	[\g_new, this.node, addActions[addAction], target];
		^bundlearray;		
		}
		
	prBundle {
		^messages ++ this.bundle;
		}

	bundle {
		var thesemsgs;
		thesemsgs = [];
		thesemsgs = thesemsgs.add(this.newBundle);
		(duration.notNil && willFree.not).if({
			thesemsgs = thesemsgs.add([(starttime + duration).asFloat, [\n_free, node]]);
			});
		^thesemsgs;
		}
		
	// create the group for RT uses
	play {//arg latency;
		var bundle = this.buildBundle;
		starttime.notNil.if({
			SystemClock.sched(starttime, {server.sendBundle(nil, bundle)});
			}, {
			server.sendBundle(nil, bundle);
			});
		duration.notNil.if({
			SystemClock.sched(duration, {this.freeAll})
			});
		this.watch;
		isGroupPlaying = true;
		^this;
		}
		
	freeAll {arg time = 0.0;
		var bund1, bund2;
		bund1 = [\g_freeAll, node];
		bund2 = [\n_free, node];
		isGroupPlaying.if({
			SystemClock.sched(time, {server.sendBundle(nil, bund1, bund2)});
			isGroupPlaying = false;
			}, {
			messages = messages.add([starttime + time, bund1, bund2]);
			})
		}
	
	deepFree {arg time = 0.0;
		this.freeAll(time);
		}
		
	}

// if a CtkBuffer is loaded to a server, its 'isPlaying' instance var will be set to true, and 
// the CtkBuffer will be considered live.

CtkBuffer : CtkObj {
	var <bufnum, <path, <size, <startFrame, <numFrames, <numChannels, <server, <bundle, 
		<freeBundle, <closeBundle, <messages, <isPlaying = false, <isOpen = false;
	var <duration, <sampleRate;
	
	*new {arg path, size, startFrame = 0, numFrames, numChannels, bufnum, server;
		^this.newCopyArgs(Dictionary.new, bufnum, path, size, startFrame, numFrames, 
			numChannels, server).init;
		}
	
	*diskin {arg path, size = 32768, startFrame = 0, server;
		^this.new(path, size, startFrame, server: server)
		}
		
	*playbuf {arg path, startFrame = 0, numFrames = 0, server;
		^this.new(path, startFrame: startFrame, numFrames: numFrames, server: server)
		}
		
	*buffer {arg size, numChannels, server;
		^this.new(size: size, numChannels: numChannels, server: server)
		}

	init {
		var sf, nFrames;
		server = server ?? {Server.default};
		bufnum = bufnum ?? {server.bufferAllocator.alloc(1)};
		messages = [];
		path.notNil.if({
			sf = SoundFile.new(path);
			sf.openRead;
			numChannels = sf.numChannels;
			duration = sf.duration;
			sampleRate = sf.sampleRate;
			sf.close;
			});
		case { // path, not size - load file with b_allocRead
			path.notNil && size.isNil
			} {
			nFrames = numFrames ?? {0};
			bundle = [\b_allocRead, bufnum, path, startFrame, nFrames];
			} {// path, size ( for DiskIn )
			path.notNil && size.notNil
			} {
			nFrames = numFrames ?? {size};
			bundle = [\b_alloc, bufnum, size, numChannels, 
				[\b_read, bufnum, path, startFrame, nFrames, 0, 1]];
			closeBundle = [\b_close, bufnum];
			} { /// just allocate memory (for delays, FFTs etc.)
			path.isNil && size.notNil
			} {
			numChannels = numChannels ?? {1};
			duration = size / server.sampleRate;
			bundle = [\b_alloc, bufnum, size, numChannels];
			};
		freeBundle = [\b_free, bufnum];
		}
	
	load {arg time = 0.0, sync = true, cond;
		SystemClock.sched(time, {
			Routine.run({
				var msg;
				cond = cond ?? {Condition.new};
				server.sendBundle(nil, bundle);
				// are there already messages to send? If yes... SYNC!, then send NOW
				(messages.size > 0).if({
					server.sync(cond);
					messages.do({arg me; 
						msg = me[1];
						server.sendBundle(nil, msg);
						server.sync(cond);
						});
					});
				sync.if({
					server.sync(cond);
					("CtkBuffer with bufnum id "++bufnum++" loaded").postln;
					});
				isPlaying = true;
				})
			});
		} 
	
	free {arg time = 0.0;
		closeBundle.notNil.if({
			SystemClock.sched(time, {
				server.sendBundle(nil, closeBundle, freeBundle);
				server.bufferAllocator.free(bufnum);
				});
			}, {
			SystemClock.sched(time, {
				server.sendBundle(nil, freeBundle);
				server.bufferAllocator.free(bufnum);
				});
			});
		isPlaying = false;
		}

	set {arg time = 0.0, startPos, values;
		var bund;
		values = values.asArray;
		// check for some common problems
		((values.size + startPos) > size).if({
			"Number of values and startPos exceeds CtkBuffer size. No values were set".warn;
			^this;
			}, {
			bund = [\b_setn, bufnum, startPos, values.size] ++ values;
			([0.0, bund].bundleSize >= 8192).if({
				"Bundle size exceeds UDP limit. Use .loadCollection. No values were set".warn;
				^this;
				}, {
				this.bufferFunc(time, bund);
				^this;
				})
			})
		}
		
	zero {arg time = 0;
		var bund;
		bund = [\b_zero, bufnum];
		this.bufferFunc(time, bund);
		}
		
	fill {arg time = 0.0, newValue, start = 0, numSamples = 1;
		var bund;
		bund = [\b_fill, bufnum, start, numSamples, newValue];
		this.bufferFunc(time, bund);
		}
	
	// write a buffer out to a file. For DiskOut usage in real-time, use openWrite and closeWrite
	write {arg time = 0.0, path, headerFormat = 'aiff', sampleFormat='int16', 
			numberOfFrames = -1, startingFrame = 0;	
		var bund;
		bund = [\b_write, bufnum, path, headerFormat, sampleFormat, numberOfFrames, 
			startingFrame, 0];
		this.bufferFunc(time, bund);
		}
	
	// prepare a buffer for use with DiskOut
	openWrite {arg time = 0.0, path, headerFormat = 'aiff', sampleFormat='int16', 
			numberOfFrames = -1, startingFrame = 0;	
		var bund;
		isOpen = true;
		bund = [\b_write, bufnum, path, headerFormat, sampleFormat, numberOfFrames, 
			startingFrame, 1];
		this.bufferFunc(time, bund);
		}
		
	closeWrite {arg time = 0.0;
		var bund;
		isOpen = false;
		bund = [\b_close, bufnum];
		this.bufferFunc(time, bund);
		}
		
	gen {arg time = 0.0, cmd, normalize = 0, wavetable = 0, clear = 1 ... args;
		var bund, flag;
		flag = (normalize * 1) + (wavetable * 2) + (clear * 4);
		bund = ([\b_gen, bufnum, cmd, flag] ++ args).flat;
		this.bufferFunc(time, bund);
		}
		
	sine1 {arg time, normalize = 0, wavetable = 0, clear = 1 ... args;
		this.gen(time, \sine1, normalize, wavetable, clear, args);
		}

	sine2 {arg time, normalize = 0, wavetable = 0, clear = 1 ... args;
		this.gen(time, \sine2, normalize, wavetable, clear, args);
		}
		
	sine3 {arg time, normalize = 0, wavetable = 0, clear = 1 ... args;
		this.gen(time, \sine3, normalize, wavetable, clear, args);
		}
		
	cheby {arg time, normalize = 0, wavetable = 0, clear = 1 ... args;
		this.gen(time, \cheby, normalize, wavetable, clear, args);
		}
		
	fillWithEnv {arg time = 0.0, env, wavetable = 0.0;
		env = (wavetable > 0.0).if({
			env.asSignal(size * 0.5).asWavetable;
			}, {
			env.asSignal(size)
			});
		this.set(time = 0.0, 0, env);
		}

	// checks if this is a live, active buffer for real-time use, or being used to build a CtkScore
	bufferFunc {arg time, bund;
		isPlaying.if({
			SystemClock.sched(time, {server.sendBundle(nil, bund)})
			}, {
			messages = messages.add([time, bund])
			});
		}
	
	asUGenInput {^bufnum}
	}
		
CtkControl : CtkObj {
	var <server, <numChans, <bus, <initValue, <starttime, <messages, <isPlaying = false, 
	<endtime = 0.0;
	var <env, <ugen, <freq, <phase, <high, <low, <ctkNote, free, <>isScored = false, 
	isLFO = false;
		
	classvar ctkEnv, sddict; 
	*new {arg numChans = 1, initVal = 0.0, starttime = 0.0, bus, server;
		^this.newCopyArgs(Dictionary.new, server, numChans, bus, initVal, starttime).initThisClass;
		}
	
	/* calling .play on an object tells the object it is being used in real-time
	and therefore will send messages to server */
	*play {arg numChans = 1, initVal = 0.0, bus, server;
		^this.new(numChans, initVal, 0.0, bus, server).play;
		}

	initThisClass {
		var bund;
		server = server ?? {Server.default};
		bus = bus ?? {server.controlBusAllocator.alloc(numChans)};
		messages = []; // an array to store sceduled bundles for this object
		bund = [\c_setn, bus, numChans, initValue];
		messages = messages.add([starttime.asFloat, bund]);
		ctkNote = nil;
		}
			
	starttime_ {arg newStarttime;
		starttime = newStarttime;
		ctkNote.notNil.if({
			ctkNote.starttime_(newStarttime);
			});
		[freq, phase, high, low].do({arg me;
			me.isKindOf(CtkControl).if({
				me.starttime_(newStarttime);
			});
		});
		}
		
	*env {arg env, starttime = 0.0, addAction = 0, target = 1, bus, server;
		^this.new(1, env[0], starttime, bus, server).initEnv(env, addAction, target);
		}
	
	initEnv {arg argenv, argAddAction, argTarget;
		var dur;
		env = argenv;
		dur = env.releaseNode.notNil.if({
			free = false;
			nil
			}, {
			free = true;
			env.times.sum;
			});
		// the ctk note object for generating the env
		ctkNote = sddict[\ctkenv].new(starttime, dur, argAddAction, argTarget, 
			server).myenv_(env).outbus_(bus);
		// add to messages here for NRT
		}
		
	*lfo {arg ugen, freq = 1, low = -1, high = 1, phase = 0, starttime = 0.0, duration,
			addAction = 0, target = 1, bus, server;
		^this.new(1, 0.0, starttime, bus, server).initLfo(ugen, freq, phase, low, high, addAction,
			target, duration);
		}
		
	initLfo {arg argugen, argfreq, argphase, arglow, arghigh, argAddAction, argTarget, argDuration;
		var thisctkno;
		ugen = argugen;
		freq = argfreq;
		phase = argphase;
		low = arglow;
		high = arghigh;
		free = false;
		messages = [];
		isLFO = true;
		thisctkno = sddict[("CTK"++ugen.class).asSymbol];
		case
			{
			[LFNoise0, LFNoise1, LFNoise2].indexOf(ugen).notNil;
			} {
			ctkNote = thisctkno.new(starttime, argDuration, argAddAction,
				argTarget, server).freq_(freq).low_(low).high_(high).bus_(bus);
			} {
			[SinOsc, Impulse, LFSaw, LFPar, LFTri].indexOf(ugen).notNil;
			} {
			ctkNote = thisctkno.new(starttime, argDuration, argAddAction, 
				argTarget, server).freq_(freq).low_(low).high_(high)
				.phase_(phase).bus_(bus);
			}			
		// add to messages here for NRT
		}
	
	freq_ {arg newfreq;
		isLFO.if({
			ctkNote.freq_(newfreq);
			})
		}

	low_ {arg newlow;
		isLFO.if({
			ctkNote.low_(newlow);
			})
		}

	high_ {arg newhigh;
		isLFO.if({
			ctkNote.high_(newhigh);
			})
		}
							
	// free the id for further use
	free {
		isPlaying = false;
		ctkNote.notNil.if({
			ctkNote.free;
			});
		server.controlBusAllocator.free(bus);
		}

	release {
		ctkNote.notNil.if({
			ctkNote.release;
			})
		}
		
	play {//arg latency = 0.1;
		var time, bund;
		isPlaying = true;
		messages.do({arg me;
			#time, bund = me;
			me.postln;
			(time > 0).if({
				SystemClock.sched(time, {
					server.sendBundle(nil, bund);
					})}, {
				server.sendBundle(nil, bund);
				});	
			});
		ctkNote.notNil.if({
			ctkNote.play;
			})
		}
		
	set {arg val, time = 0.0; //, latency = 0.1;
		var bund;
		bund = [\c_setn, bus, numChans, val];
		isPlaying.if({
			SystemClock.sched(time, {server.sendBundle(nil, bund)});
			}, {
			time = time ?? {0.0};
			messages = messages.add([starttime + time, bund]);
			});
		initValue = val;
		^this;
		}
	
	asUGenInput {^bus}
	
	*initClass {
		var thisctkno;
		sddict = CtkProtoNotes(
			SynthDef(\ctkenv, {arg gate = 1, outbus;
				Out.kr(outbus,
					EnvGen.kr(
						Control.names([\myenv]).kr(Env.newClear(16)), 
						gate, doneAction: 2))
				})
			);
			[LFNoise0, LFNoise1, LFNoise2].do({arg ugen;
				thisctkno = SynthDef(("CTK" ++ ugen.class).asSymbol, {arg freq, low, high, bus;
					Out.kr(bus, ugen.kr(freq).range(low, high));
					});
				sddict.add(thisctkno);
				});
			[SinOsc, Impulse, LFSaw, LFPar, LFTri].do({arg ugen;
				thisctkno = 
					SynthDef(("CTK" ++ ugen.class).asSymbol, {arg freq, low, high, phase, bus;
						Out.kr(bus, ugen.kr(freq, phase).range(low, high));
					});
				sddict.add(thisctkno);
				});
		
		}
	}

// not really needed... but it does most of the things that CtkControl does
CtkAudio : CtkObj {
	var <server, <bus, <numChans;
	*new {arg bus, numChans = 1, server;
		^this.newCopyArgs(Dictionary.new, server, bus, numChans).init;
		}

	// free the id for further use
	free {
		server.audioBusAllocator.free(bus);
		}
			
	init {
		server = server ?? {Server.default};
		bus = bus ?? {server.audioBusAllocator.alloc(numChans)};
		}
		
	asUGenInput {^bus}
	}
	
/* this will be similar to ProcMod ... global envelope magic 

CtkEvent can return and play a CtkScore - individual group, envbus?

with .play - needs to act like ProcMod
with .write, needs to act like CtkScore
with .addToScore - needs to act like .write, and return the CtkScore that is created, and append
	them

need to create a clock like object that will wait in tasks, advance time in .write situations
*/

/* CtkTimer needs to be a TempoClock when played, a timekeeper when used for NRT */

CtkTimer {
	var starttime, <curtime, <clock, ttempo, rtempo, isPlaying = false, <next = nil;
	
	*new {arg starttime = 0.0;
		^super.newCopyArgs(starttime, starttime);
		}
	
	play {arg tempo = 1;
		isPlaying.not.if({
			clock = TempoClock.new;
			clock.tempo_(ttempo = tempo);
			rtempo = ttempo.reciprocal;
			isPlaying = true;
			}, {
			"This CtkClock is already playing".warn
			});
		}
	
	beats {
		^this.curtime;
		}
		
	free {
		isPlaying.if({
			clock.stop;
			isPlaying = false;
			})
		}
	
	now {
		isPlaying.if({
			^clock.elapsedBeats;
			}, {
			^curtime - starttime;
			})
		}
		
	wait {arg inval;
		isPlaying.if({
			(inval*rtempo).yield;
			}, {
			curtime = curtime + inval
			});
		}
		
	next_ {arg inval;
		next = inval;
		isPlaying.not.if({
			curtime = curtime + inval;
			})
		}
	}
	
CtkEvent : CtkObj {
	classvar envsd, addActions;
	var starttime, <>condition, <function, amp, <server, addAction, target, isRecording = false;
	var isPlaying = false, isReleasing = false, releaseTime = 0.0, <timer, clock, 
		<envbus, inc, <group, <>for = 0, <>by = 1, envsynth, envbus, playinit, notes, 
		score, <endtime, endtimeud;
	
	*new {arg starttime = 0.0, condition, amp = 1, function, addAction = 0, target = 1, server;
		^super.newCopyArgs(Dictionary.new, starttime, condition, function, amp, server,
			addActions[addAction], target).init;
		}
		
	init {
		server = server ?? {Server.default};
		timer = CtkTimer.new(starttime);
		(condition.isKindOf(Env) and: {condition.releaseNode.isNil}).if({
			endtime = condition.times.sum;
			endtimeud = false
			}, {
			endtime = starttime;
			endtimeud = true;
			});
		inc = 0;
		playinit = true;
		notes = [];
		function = function ?? {{}};
		}
		
	function_ {arg newfunction;
		function = newfunction;
		}
	
	record {
		score = CtkScore.new;
		isRecording = true;
		this.play;
		^score;
		}
		
	play {
		var loopif, initVal, initSched;
		server.serverRunning.if({
			isPlaying.not.if({
				isPlaying = true;
				timer.play;
				this.setup;
				clock.sched(starttime, {
					var now;
					now = timer.now;
					playinit.if({
						playinit = false;
						condition.isKindOf(Env).if({
							condition.releaseNode.isNil.if({
								clock.sched(condition.times.sum + 0.1, {this.clear});
								})
							});
						[group, envbus, envsynth].do({arg me; 
							me.notNil.if({
								isRecording.if({
									score.add(me)
									});	
								me.play;
								})
							});
						});
					function.value(this, group, envbus, inc, server);
					this.run;
					this.checkCond.if({
						timer.next;
						}, {
						initSched = (endtime > timer.now).if({endtime - timer.now}, {0.1});
						timer.clock.sched(initSched, {
							(group.children.size == 0).if({
								this.free;
								}, {
								0.1;
								})
							})
						});
					})
				})
			}, {
			"Please boot the Server before trying to play an instance of CtkEvent".warn;
			})
		}
	
	run {
		notes.asArray.do({arg me;
			me.starttime.isNil.if({
				isRecording.if({score.add(me.copy.starttime_(timer.now))});
				me.play(nil);
				}, {
				clock.sched(me.starttime, {
					isPlaying.if({
						isRecording.if({
							score.add(me.copy(timer.now))
							});
						me.play(nil, group);
						});
					})
				});
			});
		notes = [];
		inc = inc + by;	
		}
		
	setup {
		var thisdur;
		group.notNil.if({group.free});
		envbus.notNil.if({envbus.free});
		clock = timer.clock;
		group = CtkGroup.new(addAction: addAction, target: target, server: server);
		condition.isKindOf(Env).if({
			envbus = CtkControl.new(initVal: condition.levels[0], starttime: starttime, 
				server: server);
			thisdur = condition.releaseNode.isNil.if({condition.times.sum}, {nil});
			envsynth = envsd.new(duration: thisdur, target: group, server: server)
				.outbus_(envbus.bus).evenv_(condition).amp_(amp);
			}, {
			envbus = CtkControl.new(1, amp, starttime, server: server);
			});
		(target.isKindOf(CtkNote) || target.isKindOf(CtkGroup)).if({
			target = target.node});
		}
	
	free {
		this.clear;
		}
	
	release {
		isPlaying.if({
			condition.isKindOf(Env).if({
				condition.releaseNode.notNil.if({
					envsynth.release(key: \evgate);
					this.releaseSetup(condition.releaseTime);
					}, {
					"The envelope for this CtkEvent doesn't have a releaseNode. Use .free instead".warn;})
				}, {
				"This CtkEvent doesn't use an Env as a condition control. Use .free instead".warn
				})
			}, {
			"This CtkEvent is not playing".warn
			});
		}
		
	releaseSetup {arg reltime;
		clock.sched(reltime, {this.clear});
		}
		
	clear {
		clock.clear;
		clock.stop;
		group.free;
		envbus.free;
		isPlaying = false;
		isRecording = false;
		this.init;
		}

	scoreClear {
		clock.clear;
		clock.stop;
		isPlaying = false;
		isRecording = false;
		this.init;
		}
			
	next_ {arg inval;
		timer.next_(inval);
		}
	
	curtime {
		^timer.curtime;
		}
	
	checkCond {
		case
			{
			timer.next == nil
			} {
			^false
			} {
			condition.isKindOf(Boolean) || condition.isKindOf(Function)
			} {
			^condition.value(timer, inc)
			} {
			condition.isKindOf(SimpleNumber)
			} {
			^inc < condition
			} {
			condition.isKindOf(Env)
			} {
			^condition.releaseNode.isNil.if({
				timer.now < condition.times.sum;
				}, {
				(isReleasing || (releaseTime < condition.releaseTime))
				})
			} {
			true
			} {
			^false
			}
		}
	
	collect {arg ... ctkevents;
		var thisend;
		endtimeud.if({
			ctkevents.do({arg ev;
				ev.endtime.notNil.if({
					thisend = ev.endtime + timer.now;
					(thisend > endtime).if({
						endtime = thisend
						})
					})
				})
			});
		notes = (notes ++ ctkevents).flat;
		}
	
	//  may not need this... or, if may be able to be simplified (just store objects to 
	// the CtkScore ... or WOW! I THINK IT WILL JUST WORK!)
	
	score {arg sustime = 0;
		var curtime,idx;
		// check first to make sure the condition, if it is an Env, has a definite duration
		condition.isKindOf(Env).if({
			condition.releaseNode.notNil.if({
				// use sustime to convert it to a finite Env
				idx = condition.releaseNode;
				condition.times = condition.times.insert(idx, sustime);
				condition.levels = condition.levels.insert(idx, condition.levels[idx]);
				condition.curves.isArray.if({
					condition.curves = condition.curves.insert(idx, \lin)
					});
				condition.releaseNode_(nil);
				});
			});
		score = CtkScore.new;
		this.setup;
		[group, envbus, envsynth].do({arg me; 
			group.node;
			me.notNil.if({me.starttime_(starttime);
			score.add(me)
			})
		});
		while({
			curtime = timer.curtime;
			function.value(this, group, envbus, inc, server);
			notes.asArray.do({arg me;
				me.starttime.isNil.if({
					me.starttime_(curtime)
					}, {
					me.starttime_(me.starttime + curtime);
					});
				score.add(me);
				});
			notes = [];
			inc = inc + by;
			this.checkCond;
			});
		this.scoreClear;
		^score;
		}
	
	*initClass {
		addActions = IdentityDictionary[
			\head -> 0,
			\tail -> 1,
			\before -> 2,
			\after -> 3,
			\replace -> 4,
			0 -> 0,
			1 -> 1,
			2 -> 2,
			3 -> 3,
			4 -> 4
			];
		StartUp.add({
		envsd = CtkNoteObject(
			SynthDef(\ctkeventenv_2561, {arg evgate = 1, outbus, amp = 1, timeScale = 1, 
					lag = 0.01;
				var evenv;
				evenv = EnvGen.kr(
					Control.names(\evenv).kr(Env.newClear(30)), evgate, 
						1, 0, timeScale, doneAction: 13) * Lag2.kr(amp, lag);
				Out.kr(outbus, evenv);
				})
			);
			})	
		}
		
	}

