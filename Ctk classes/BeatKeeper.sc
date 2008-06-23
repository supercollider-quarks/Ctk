// BeatKeeper is a model for dealing with sensible musical time or things like notation. 
// it basically introduces some rounding for starttimes, so things like triplets within a beat
// don't introduce a constant drift away from whole numbers.

BeatKeeper {
	var <now;
	
	*new {arg now;
		^super.newCopyArgs(now);
		}

	// wait advances and rounds now according to the duration of a beat
	wait {arg waittime, beatDur = 0.25, tolerance = 0.0001, base = 0.25;
		now = now + (waittime / beatDur);
		this.roundNow(tolerance, base);
		}
	
	// by default, round to the nearest quarter beat
	roundNow {arg tolerance = 0.0001, base = 0.25;
		var tmp, diff;
		tmp = now.round(base);
		diff = (now - tmp).abs;
		(diff < tolerance).if({now = tmp});
		}
		
	now_ {arg newNow, tolerance = 0.0001, base = 0.25;
		now = newNow;
		this.roundNow(tolerance, base);
		}	
}