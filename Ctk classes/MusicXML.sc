XMLMusicObj {
	classvar durToTypes, symToTypes, symToDur, baseDurs, accToAlter;
	var <duration, <type;
	
	*initClass {
		/* incorporate dotted values */
		durToTypes = IdentityDictionary[
			0.25 -> "quarter",			
			0.125 -> "eighth",
			0.0625 -> "16th",
			0.03125 -> "32nd",
			0.015625 -> "64th",
			0.0078125 -> "128th",
			0.00390625 -> "256th",
			0.5 -> "half",
			1.0 -> "whole",
			2.0 -> "breve",
			4.0 -> "long"
			];
		symToTypes = IdentityDictionary[
			\q -> "quarter",
			\qd -> "quarter", 		
			\qdd -> "quarter", 
			\e -> "eighth",
			\ed -> "eighth",
			\edd -> "eighth",
			\s -> "16th",
			\sd -> "16th",
			\sdd -> "16th",
			\t -> "32nd",
			\td -> "32nd",
			\tdd -> "32nd",
			\x -> "64th",
			\xd -> "64th",
			\xdd -> "64th",
			\o -> "128th",
			\od -> "128th",
			\odd -> "128th",
			\h -> "half",
			\hd -> "half",
			\hdd -> "half",
			\w -> "whole",
			\wd -> "whole",
			\wdd -> "whole",
			\b -> "breve",
			\bd -> "breve",
			\bdd -> "breve",
			\l -> "long",
			\ld -> "long",
			\ldd -> "long"
			];
		symToDur = IdentityDictionary[
			\q -> 0.25,
			\qd -> 0.375,
			\qdd -> 0.4375,			
			\e -> 0.125,
			\ed -> 0.1875,
			\edd -> 0.21875,
			\s -> 0.0625,
			\sd -> 0.09375,
			\sdd -> 0.109375,
			\t -> 0.03125,
			\td -> 0.046875,
			\tdd -> 0.0546875,
			\x -> 0.015625,
			\xd -> 0.0234375,
			\xdd -> 0.02734375,
			\o -> 0.0078125,
			\od -> 0.01171875,
			\odd -> 0.013671875,
			\h -> 0.5,
			\hd -> 0.75,
			\hdd -> 0.875,
			\w -> 1.0,
			\wd -> 1.5,
			\wdd -> 1.75,
			\b -> 2.0,
			\bd -> 3.0,
			\bdd -> 3.5,
			\l -> 4.0,
			\ld -> 6.0,
			\ldd -> 7.0
			];
		baseDurs = [ 0.00390625, 0.0078125, 0.015625, 0.03125, 0.0625, 0.125, 0.25, 0.5, 1, 2, 4 ];
		accToAlter = IdentityDictionary[
			\ffff -> -4,
			\fff -> -3,
			\ff -> -2,
//			\tqf -> -1.5,
			\f -> -1,
//			\qf -> -0.5,
			\n -> 0,
//			\qs -> 0.5,
			\s -> 1,
//			\tqs -> 1.5,
			\ss -> 2,
			\sss -> 3,
			\ssss -> 4]
		}
		
	duration_ {arg newDuration, tuplet;
		var idx;
		type = case
			{newDuration.isKindOf(SimpleNumber)}
			{durToTypes[newDuration.asFloat]}
			{newDuration.isKindOf(Symbol)}
			{symToTypes[newDuration]}
			{true}
			{nil};
		// need to find the basic value (then, dots, etc....)
/*
a = [0.015625, 0.03125, 0.0625, 0.125, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0];

a.indexOfGreaterThan(0.5) - 1
*/
		duration = newDuration * tuplet.notNil.if({tuplet.reciprocal}, {1});
		(type.isNil and: {newDuration.isKindOf(SimpleNumber)}).if({
//			"My type is nil!".postln;
			idx = baseDurs.indexInBetween(newDuration).floor;
			type = durToTypes[baseDurs[idx]]
			});
//		"Duration".postln;
//		[type, duration].postln;
		}
	
	/* this isn't quite right - it should be a measure that is passed in - what if a note is 
	held over from a measure? Multiple measures? We need to calculate all of that */
	numBeats {arg anXMLMeter;
		var thisDur, tmpDur;
		anXMLMeter = anXMLMeter ?? {XMLMeter(4, 4)};
		thisDur = duration.isKindOf(Symbol).if({symToDur[duration]}, {duration});
		tmpDur = (thisDur * anXMLMeter.lower);
		^(anXMLMeter.type != \compound).if({tmpDur}, {tmpDur / 3});
		}
	}

// holds voices, and outputs the actual MusicXML file

XMLScore {
	var <score, file, <doc;
	*new {
		^super.new.initXMLScore;
		}
		
	initXMLScore {
		score = [];
		doc = DOMDocument.new;
		}
		
	add {arg ... voices;
		voices.do({arg aVoice;
			aVoice.isKindOf(XMLVoice).if({
				score = score.add(aVoice);
				}, {
				"XMLScores can only add an XMLVoice".warn
				})
			});
		}
			
	output {arg pathname;
		file = File.new(pathname, "w");
		file.putString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE score-partwise PUBLIC \"-/"++"/Recordare/"++"/DTD MusicXML 1.0 Partwise/"++"/EN\"\n\t\t\t\t\"http:/"++"/www.musicxml.org/dtds/partwise.dtd\">\n");
		this.populateScore;
         doc.write(file);
         file.close;
		}
		
	populateScore {
		var scorePartwise, partList, scorePart ;
		doc.appendChild(scorePartwise = doc.createElement("score-partwise")
			.setAttribute("version", "1.0"));
		scorePartwise.appendChild(partList = doc.createElement("part-list"));
		score.sort({arg a, b; (a.id == b.id).if({
				^"AAAAAH!!! you have parts with the same id... very bad, I won't even try it!".warn;});
			a.id < b.id});
		score.do({arg me, i;
			partList.appendChild(scorePart = doc.createElement("score-part")
				.setAttribute("id", "P"++me.id));
			scorePart.appendChild(doc.createElement("part-name")
				.appendChild(doc.createTextNode(me.partName.asString)));
				});
		score.do({arg me;
			me.addToDoc(doc, scorePartwise);
			});
		}


}

/* build in some info type things - where is beat x? etc. 
perhaps also a way to insert measures (so - offset what is already there, and shift data) */

XMLVoice {
	var <partName, <id, measures, <notes, <timeSigs, <keySigs, <clefs;
	var <timeSigMeasures, <timeSigInstances, <keySigMeasures, <keySigInstances, 
		<clefMeasures, <clefInstances, sortNeeded;
	var curMeter, curClef, curKey, <beatDurArray, <measureBeatArray;
	var beatKeeper;
	
	*new {arg partName, id = 1, meter, key, clef;
		^this.newCopyArgs(partName, id).initXMLVoice(meter, key, clef);
		}
		
	initXMLVoice {arg meter, key, clef;
		timeSigs = [];
		keySigs = [];
		clefs = [];
		meter.notNil.if({this.addMeter(1, meter)});
		key.notNil.if({this.addKey(1, key)});
		clef.notNil.if({this.addClef(1, clef)});
		measures = [];
		beatDurArray = [];
		sortNeeded = true;
		beatKeeper = BeatKeeper.new(1.0);
		}
	
	now {
		^beatKeeper.now
		}
		
	now_ {arg newNow;
		beatKeeper.now_(newNow);
		}
		
	incNextBeat {
		beatKeeper.now_(beatKeeper.now.ceil);
		}
	/* perhaps add a 'shift' flag. If there is data after this, should it shift? Or, better yet
	make an insert measure function */
	
	addMeter {arg measure, meter;
		meter.isKindOf(XMLMeter).if({
			timeSigs = timeSigs.add([measure, meter]);
			sortNeeded = true;
			}, {
			"Only XMLMeter objects can be added as a meter".warn
			});	
		}

	addKey {arg measure, key;
		key.isKindOf(XMLKey).if({
			keySigs = keySigs.add([measure, key]);
			sortNeeded = true;
			}, {
			"Only XMLkey objects can be added as a key".warn
			});	
		}
		
	addClef {arg measure, clef;
		clef.isKindOf(XMLClef).if({
			clefs = clefs.add([measure, clef]);
			sortNeeded = true;
			}, {
			"Only XMLClef objects can be added as a clef".warn
			});	
		}
		
	// you add notes to an XMLVoice, but when it is finally sent as output, it needs to be 
	// measures (which contain attributes, XMLNotes and XMLRests)
	sendToMeasures {
		var notesCopy, curMeasure, curBeat, thisMeasure, thisMeter, thisKey, thisClef, thisNote, 
			numBeats, thisBeat, endBeat, rest;
		notes.sort({arg a, b; a.beat < b.beat});
		notesCopy = this.fillWithRests(notes); //notes.deepCopy;//.reverse;
		curMeasure = 1;
		curBeat = 1;
		// sort the attribute list
		sortNeeded.if({this.sortVoiceAttributes});
		#curMeter, curKey, curClef = this.getAttributes(curMeasure);
		thisMeasure = XMLMeasure.new(curMeasure, curMeter, curClef, curKey, 1);
		numBeats = curMeter.numBeats;
		endBeat = numBeats + 1;
		thisNote = notesCopy.pop;
		// iterate over notes - get the needed one's, add rests! DO THIS!!!
		thisNote.notNil.if({
			while({
				(thisNote.beat < thisMeasure.lastBeat).if({
					thisMeasure.addNote(thisNote);
					}, {
					while({
						measures = measures.add(thisMeasure);
						curMeasure = curMeasure + 1;
						#curMeter, curKey, curClef = this.getAttributes(curMeasure);
						thisMeasure = XMLMeasure.new(curMeasure, curMeter, curClef, curKey,
						 	thisMeasure.lastBeat);
						(thisNote.beat >= thisMeasure.lastBeat)
						});
					thisMeasure.addNote(thisNote);
					curBeat = thisNote.endBeat(curMeter);
					});
				thisNote = notesCopy.pop;
				thisNote.notNil;
				});
			});
		measures = measures.add(thisMeasure);
		}
	
	fillWithRests {arg notes;
		var tmp, idx = 0, rests, numBeats, beatDur;
		var wholebeats, partialbeats;
		rests = [];
		// check if first note is in beat 1
		(notes[0].beat > 1).if({
			wholebeats = notes[0].beat.floor - 1;
			wholebeats.do({arg i;
				rests = rests.add(XMLRest(i + 1, this.getBeatDurFromBeat(i + 1)))
				});
			partialbeats = notes[0].beat - notes[0].beat.floor;
			rests = rests.add(XMLRest(notes[0].beat.floor, partialbeats * 
				this.getBeatDurFromBeat(notes[0].beat.floor)));
			});
		(notes.size - 1).do({arg i;
			var note1, note2, dif;
			note1 = notes[i];
			note2 = notes[i + 1];
			dif = note2.beat - note1.endBeat;
			(dif > 0.000001).if({
				// we need at least a rest! are these two notes within the same beat?
				((note2.beat.floor - note1.endBeat.floor) <= 1).if({
					beatDur = this.getBeatDurFromBeat(note1.endBeat.floor);
					rests = rests.add(XMLRest(note1.endBeat, dif * beatDur));
					}, {
					beatDur = this.getBeatDurFromBeat(note1.endBeat.floor);
					// first - fill this beat
					rests = rests.add(
						tmp = XMLRest(note1.endBeat, 
							((note1.endBeat.floor + 1) - note1.endBeat) * beatDur));
					// how many whole beats do we need?
					(note2.beat - tmp.endBeat).floor.do({arg i;
						var thisBeat;
						thisBeat = note1.endBeat + i + 1;
						beatDur = this.getBeatDurFromBeat(thisBeat);
						rests = rests.add(XMLRest(thisBeat, beatDur));
						});
					((note2.beat - note2.beat.floor) > 0).if({
						beatDur = this.getBeatDurFromBeat(note2.beat.floor);
						rests = rests.add(
							XMLRest(note2.beat.floor, 
								(note2.beat - note2.beat.floor) * beatDur));
						});
					})
				});
			});
		tmp = notes.deepCopy ++ rests;
		tmp.sort({arg a, b; a.beat < b.beat});
		^tmp.reverse;
		}
			
	sortVoiceAttributes {
		timeSigs.sort({arg a, b; (a[0] == b[0]).if({
				"It appears that measure " ++ a[0] ++ " has two time signatures assigned to it... things probably won't look right!".warn;
				});
			a[0] < b[0]
			});
		#timeSigMeasures, timeSigInstances = timeSigs.flop;
		keySigs.sort({arg a, b; (a[0] == b[0]).if({
				"It appears that measure " ++ a[0] ++ " has two key signatures assigned to it... things probably won't look right!".warn;
				});
			a[0] < b[0]
			});
		#keySigMeasures, keySigInstances = keySigs.flop;
		clefs.sort({arg a, b; (a[0] == b[0]).if({
				"It appears that measure " ++ a[0] ++ " has two clefs assigned to it... things probably won't look right!".warn;
				});
			a[0] < b[0]
			});
		#clefMeasures, clefInstances = clefs.flop;
		sortNeeded = false;
		}
	
	getAttributes {arg curMeasure;
		var thisMeter, thisKey, thisClef, idx;
		/* set up the first measure, get the current attributes */
		thisMeter = (timeSigs.size > 0 and: {timeSigMeasures[0] == 1}).if({
			idx = timeSigMeasures.indexInBetween(curMeasure).floor;
			timeSigInstances[idx];
			}, {
			curMeter ?? {XMLMeter(4, 4)};
			});
		thisKey = (keySigs.size > 0 and: {keySigMeasures[0] == 1}).if({
			idx = keySigMeasures.indexInBetween(curMeasure).floor;
			keySigInstances[idx];
			}, {
			curKey ?? {XMLKey.major(\C)};
			});
		thisClef = (clefs.size > 0 and: {clefMeasures[0] == 1}).if({
			idx = clefMeasures.indexInBetween(curMeasure).floor;
			clefInstances[idx];
			}, {
			curClef ?? {XMLClef.treble}
			});
		^[thisMeter, thisKey, thisClef];
		} 
	
		
	add {arg ... noteEvents;
		noteEvents.do({arg me;
			notes = notes.add(me);
			(me.beat >= beatKeeper.now).if({
				beatKeeper.now_(me.beat);
				beatKeeper.wait(me.duration, this.getBeatDurFromBeat(me.beat));
				})
			});
		}
		
	addToDoc {arg doc, scorePartwise;
		var part, attribute, note;
		scorePartwise.appendChild(
			part = doc.createElement("part").setAttribute("id", "P"++id));
		this.sendToMeasures;
		measures.do({arg measure;
			measure.appendAttributes(doc, part)
			});
		}
	
	// fills an array with the number of beats in each measure... e.g:
	// [4, 4, 4, 3, 4, 4] tells you measure 1, 2, 4 and 5 have 4 beats, measure 3 has 3
	// fist element is a 0th measure, that really doesn't exist. makes indexing easier.
	fillMeasureBeatArray {
		var idx;
		sortNeeded.if({this.sortVoiceAttributes});
		measureBeatArray = (timeSigMeasures.maxItem + 1).collect({arg curMeasure;
			curMeasure = curMeasure;
			idx = timeSigMeasures.indexInBetween(curMeasure).floor;
			timeSigInstances[idx].numBeats;
			});
		^measureBeatArray;
		}
	
	// returns the measure and beat where a beat exists
	getMeasureFromBeat {arg beat;
		var measure = 1, tmpBeat, curBeat, theseBeats, maxSize, idx;
		tmpBeat = beat;
		this.fillMeasureBeatArray;
		maxSize = measureBeatArray.size - 1;
		curBeat = 1 + (theseBeats = measureBeatArray[measure]);
		(curBeat > beat).if({
			^[measure, beat]
			}, {
			while({
				tmpBeat = tmpBeat - theseBeats;
				measure = measure + 1;
				idx = measure.min(maxSize);
				theseBeats = measureBeatArray[idx];
				curBeat = curBeat + measureBeatArray[idx];
				curBeat <= beat;
				});
			^[measure, tmpBeat]
			});
		}
		
	getBeatDurFromBeat {arg beat;
		var thisMeasure, thisBeat, meter, key, clef, beatDurInMeasure;
		#thisMeasure, thisBeat = this.getMeasureFromBeat(beat);
		#meter, key, clef = this.getAttributes(thisMeasure);
		meter.upper.isKindOf(Array).if({
			beatDurInMeasure = meter.upper[thisBeat - 1];
			}, {
			beatDurInMeasure = (meter.type == \compound).if({3}, {1}); //meter.upper;
			});
		^meter.lower.reciprocal * beatDurInMeasure;
		}

	getBeatDur {arg beat;
		var beatDurArraySize;
		sortNeeded.if({this.sortVoiceAttributes});
		^((beat - 1) < beatDurArraySize = beatDurArray.size).if({
			beatDurArray[beat - 1]
			}, {
			beatDurArray[beatDurArraySize - 1]
			})
			
		}
		
	getMeterForMeasure {arg measure;
		var idx;
		^(timeSigs.size > 0 and: {timeSigMeasures[0] == 1}).if({
			idx = timeSigMeasures.indexInBetween(measure).floor;
			timeSigInstances[idx];
			}, {
			curMeter ?? {XMLMeter(4, 4)};
			});	
		}
		
	getMeterFromBeat {arg beat;
		var measure;
		measure = this.getMeasureFromBeat(beat);
		^this.getMeterForMeasure(measure[0]);
		}
}

XMLEvent {


}

XMLNote : XMLMusicObj {
	var <>beat, <tuplet, <note, <pc;
	
	*new {arg aPitchClass, beat = 1.0, duration = 1.0, tuplet;
		^super.newCopyArgs(nil, nil, beat, tuplet).initXMLNote(aPitchClass, duration);
		}
		
	initXMLNote {arg argAPitchClass, argDuration;
		var tmp;
		this.note_(argAPitchClass);
		// I don't like this - find a way to save the symbol 
		tmp = argDuration.isKindOf(Symbol).if({
			symToDur[argDuration]
			}, {
			argDuration
			});
		this.duration_(tmp, tuplet);
		}
		
	note_ {arg aPitchClass;
		pc = aPitchClass.isKindOf(Number).if({PitchClass(aPitchClass)}, {aPitchClass});
		}
	
	beatsInMeter {arg beat = 1, anXMLMeter;
		anXMLMeter = anXMLMeter ?? {XMLMeter(4, 4)};
		^(duration / anXMLMeter.beatDur(beat))
		}
		
	endBeat {arg anXMLMeter;
		^beat + this.numBeats(anXMLMeter);
		}

	appendToMeasure {arg tree, doc, divisions = 1;
		var thisNote, thisType, idx, leftover;
		var pitch, step, alter, octave, thisDuration, tmpDur, tupletTop, tupletBottom, timeMod,
			actual, normal;
		thisNote = doc.createElement("note");
		pitch = doc.createElement("pitch");
		step = doc.createElement("step");
		step.appendChild(doc.createTextNode((pc.note.asString)[0].toUpper.asString));
		alter = doc.createElement("alter");	
		alter.appendChild(doc.createTextNode(accToAlter[pc.acc].asString));
		octave = doc.createElement("octave");
		octave.appendChild(doc.createTextNode(pc.octave.asInteger.asString));
		pitch.appendChild(step);
		pitch.appendChild(alter);
		pitch.appendChild(octave);
		thisNote.appendChild(pitch);
		tmpDur = duration.isKindOf(Symbol).if({symToDur[duration]}, {duration});
		thisDuration = doc.createElement("duration").appendChild(
				doc.createTextNode(
					((tmpDur * divisions).round * 4).asString));
		thisNote.appendChild(thisDuration);
		thisType = doc.createElement("type").appendChild(doc.createTextNode(type));
		thisNote.appendChild(thisType);
		tuplet.notNil.if({
			#tupletTop, tupletBottom = tuplet.asFraction(768, false);
			timeMod = doc.createElement("time-modification");
			thisNote.appendChild(timeMod);
			actual = doc.createElement("actual-notes");
			timeMod.appendChild(actual);
			actual.appendChild(doc.createTextNode(tupletTop.asString));
			normal = doc.createElement("normal-notes");
			timeMod.appendChild(normal);
			normal.appendChild(doc.createTextNode(tupletBottom.asString));
			});
		tree.appendChild(thisNote);
		}
	
}

XMLMelody {


}

XMLRest  : XMLMusicObj {
	var <>beat, <tuplet;
	
	*new {arg beat = 1.0, duration = 1.0, tuplet;
		^super.newCopyArgs(nil, nil, beat, tuplet).initXMLRest(duration);
		}
		
	initXMLRest {arg argDuration;
		var tmp;
		tmp = argDuration.isKindOf(Symbol).if({
			symToDur[argDuration]
			}, {
			argDuration
			});
		this.duration_(tmp, tuplet);
		}

	endBeat {arg anXMLMeter;
		^beat + this.numBeats(anXMLMeter);
		}
		
	appendToMeasure {arg tree, doc, divisions = 1;
		var thisNote;
		var pitch, step, alter, octave, thisDuration, tmpDur;
		thisNote = doc.createElement("note");
		tree.appendChild(thisNote);
		thisNote.appendChild(doc.createElement("rest"));
		tmpDur = duration.isKindOf(Symbol).if({symToDur[duration]}, {duration});
		thisDuration = doc.createElement("duration").appendChild(
				doc.createTextNode(
					((tmpDur * divisions).round * 4).asString));
		thisNote.appendChild(thisDuration);
		tree.appendChild(thisNote);
		}

}

XMLMeter {
	var <>upper, <>lower, <type, <division;
	// numBeats is going to be the important one here!
	var <numBeats, <beatLength, <measureLength;
	
	*new {arg upper, lower, type = \simple;
		^super.newCopyArgs(upper, lower, type).initXMLMeter
		}
	
	initXMLMeter {
		upper.isKindOf(Array).if({
			numBeats = upper.size;
			}, {
			(type == \compound).if({
				numBeats = upper / 3;
				}, {
				numBeats = upper;
				});
			});
		}
	
	beatDur {arg beat = 1;
		upper.isKindOf(Array).if({
			(beat <= upper.size).if({
				^upper[beat - 1] * lower.reciprocal;
				}, {
				("This meter has "++upper.size++" beats, you asked for "++beat).warn;
				^nil;
				})
			}, {
			(type == \compound).if({
				^lower.reciprocal * 3;
				}, {
				^lower.reciprocal
				})
			});
		}
		
	measureDur {
		var lowerdur = lower.reciprocal;
		(upper.isKindOf(Array)).if({
			^upper.sum * lowerdur;
			}, {
			^upper * lowerdur
			});
		}
		
	// need to see how to do complex uppers
	appendAttributes {arg tree, doc;
		var upperString = "";
		(upper.isKindOf(Array)).if({
			upper.do({arg me, i;
				(i == 0).if({
					upperString = upperString ++ me;
					}, {
					upperString = upperString ++ "+" ++ me;
					})
				})
			}, {
			upperString = upper.asString;
			});
		tree.appendChild(
			doc.createElement("time").appendChild(
				doc.createElement("beats").appendChild(
					doc.createTextNode(upperString))).appendChild(
				doc.createElement("beat-type").appendChild(
					doc.createTextNode(lower.asString))));
		}



}

// clef type is \c, \g, \f, \p or \none???
XMLClef {	
	var <clefType, line, name;
	
	*new {arg clefType, line;
		^super.newCopyArgs(clefType, line).initXMLClef;
		}
		
	initXMLClef {
	
		}
		
	*treble {
		^this.new(\G, 2);
		}
		
	*bass {
		^this.new(\F, 4);
		}
		
	*alto {
		^this.new(\C, 3);
		}
		
	*soprano {
		^this.new(\C, 1);
		}
		
	*tenor {
		^this.new(\C, 4);
		}
		
	appendAttributes {arg tree, doc;
		tree.appendChild(
			doc.createElement("clef").appendChild(
				doc.createElement("sign").appendChild(
					doc.createTextNode(clefType.asString))).appendChild(
				doc.createElement("line").appendChild(
					doc.createTextNode(line.asString))));	
		}
	}

XMLKey {
	classvar majorToAcc, minorToAcc;
	var <accidentals, <mode;
	
	*new {arg accidentals = 0, mode = \major;
		^super.newCopyArgs(accidentals, mode).initXMLKey;
		}
		
	initXMLKey {
	
		}
	
	*major {arg key;
		^this.new(majorToAcc[key], \major);
		}
		
	*minor {arg key;
		^this.new(minorToAcc[key], \minor);
		}
			
	appendAttributes {arg tree, doc;
		tree.appendChild(
			doc.createElement("key").appendChild(
				doc.createElement("fifths").appendChild(
					doc.createTextNode(accidentals.asString))).appendChild(
				doc.createElement("mode").appendChild(
					doc.createTextNode(mode.asString))))
	
		}
		
	*initClass {
		majorToAcc = IdentityDictionary[
			\C -> 0,
			\G -> 1,
			\D -> 2,
			\A -> 3,
			\E -> 4,
			\B -> 5,
			\Fs -> 6,
			\Cs -> 7,
			\F -> -1,
			\Bf -> -2,
			\Ef -> -3,
			\Af -> -4,
			\Df -> -5,
			\Gf -> -6,
			\Cf -> -7];
		minorToAcc = IdentityDictionary[
			\a -> 0,
			\e -> 1,
			\b -> 2,
			\fs -> 3,
			\cs -> 4,
			\gs -> 5,
			\ds -> 6,
			\as -> 7,
			\d -> -1,
			\g -> -2,
			\c -> -3,
			\f -> -4,
			\bf -> -5,
			\ef -> -6,
			\af -> -7
			]
		}

}

/* user probably won't see XMLMeasures. XMLVoice will create these */
XMLMeasure {
	var <measureNumber, <meter, <clef, <key, <firstBeat, <notes, <>divisions = 240, <key, <time,
		<lastBeat, <numBeats;
	
	*new {arg measureNumber, meter, clef, key, firstBeat = 1 ... notes;
		^super.newCopyArgs(measureNumber, meter, clef, key, firstBeat, notes).initXMLMeasure;
		}
		
	initXMLMeasure {
		numBeats = meter.numBeats;
		// this beat doesn't exist in this measure .. it is the first beat of the next measure
		lastBeat = firstBeat + numBeats;
		}
	
	addNote {arg ... newNotes;
		notes = notes ++ newNotes;
		}
		
	appendAttributes {arg doc, part;
		var measure, tree, thesedurs;
//		notes.removeAllSuchThat({arg me; me.duration < 0.00001});
		thesedurs = notes.collect({arg me; me.duration});
		thesedurs = thesedurs.select({arg me; me > 0});
		thesedurs = thesedurs.collect({arg me; var tmp; tmp = me.asFraction(768, false); tmp[1]});
		thesedurs.postln;
		thesedurs.removeAllSuchThat({arg me, i; me < 0});
		divisions = thesedurs.reduce(\lcm);
		
		measure = doc.createElement("measure").setAttribute("number", measureNumber.asString);
		measure.appendChild(tree = doc.createElement("attributes"));
		tree.appendChild(
			doc.createElement("divisions").appendChild(
					doc.createTextNode(divisions.asString)));
		key.appendAttributes(tree, doc);
		meter.appendAttributes(tree, doc);
		clef.appendAttributes(tree, doc);
		measure.appendChild(tree);
		

		notes.flat.do({arg me;
			(me.duration > 0).if({
				me.appendToMeasure(measure, doc, thesedurs.maxItem);
				})
			});
		// append to the part
		part.appendChild(measure);
		}


}

XMLChord {


}