package de.delphi.vrc7j;

/**
 * One operator of the VRC7. The operator generates a signal based on phase input and amplitude input.
 * @author Delphi1024
 */
/*package*/ class Operator {
	
	/*package*/ static int[] expTable=new int[256];
	
	/*package*/ static int[] logSinTable=new int[256];
	
	private static int[] fastExpTable=new int[1<<13];
	
	/*package*/ static final int IN_MASK=(1<<20)-1,
							OUT_MASK=(1<<20)-1;
	
	//VRC7/OPLL base frequency
	/*package*/ static final double OPERATOR_CLOCK=3579545.0/72.0;
	
	static {
		//Generate logSin and exp tables
		double log2Scale=Math.log10(2.0);
		for(int i=0;i<expTable.length;i++) {
			expTable[i]=(int) Math.round((Math.pow(2, i/256.0)-1)*1024);
			logSinTable[i]=(int) Math.round(-Math.log10(Math.sin((i+0.5)*Math.PI/512.0))/log2Scale*256.0);
		}
		
		for(int i=0;i<fastExpTable.length;i++) {
			fastExpTable[i]=dbToLinear(i) & OUT_MASK;
		}
	}
	
	/**
	 * Convert "decibel" value to linear. The input dB value is expected to be 11 bits wide.
	 * @param db dB value to convert
	 * @return Linear value according to the exp table
	 */
	public static int dbToLinear(int db) {
		int shift=0b111^Math.min(db>>>8,7);
		db=expTable[(db & 0xff)^0xff];	//10 bit
		db+=1024;				//11 bit
		db<<=shift+2;
		return db;
	}
	
	/**
	 * Returns log(sin(phase)), using the logSin table. The phase is expected to be 18 bits wide, although the most significant bit (sign bit of the output)
	 * is not used by this function. This means that the output is logSin for phases 0 to pi, and the phases pi to 2pi have to be created by negating the output.
	 * @param phase Input phase
	 * @return Corresponding entry in the logSin table
	 */
	public static int logSin(int phase) {
		if((phase & (1<<16))!=0)
			phase^=IN_MASK;
		return logSinTable[(phase>>>8) & 0xff];
	}
	
	//Frequency multiplier
	private int mult=1;
	
	//Phase counter (18 bit) and phase counter increment
	private int phase=0,phaseInc=0;
	
	//Previous output
	private int prevOutput=0;
	
	//F-number and octave
	@SuppressWarnings("unused")
	private int fNum=0,octave;
	
	//Whether half-rectified waveform is selected
	private boolean rectify;
	
	public Operator() {
		
	}
	
	public void setFNumber(int fNum,int octave) {
		this.octave=octave;
		this.fNum=fNum;
		phaseInc=fNum<<octave;
	}
	
	/**
	 * Selects between normal and half-rectified waveform.
	 * @param rectify 
	 */
	public void setRectify(boolean rectify) {
		this.rectify=rectify;
	}
	
	/**
	 * Restarts the operator.
	 */
	public void start() {
		phase=0;
		prevOutput=0;
	}
	
	/**
	 * Sets the multiplier for this operator. Used to set the ratio between the modulator's and carrier's frequency.
	 * @param mult
	 */
	public void setMultiplier(int mult) {
		this.mult=mult;
	}
	
	/**
	 * Fetches a single sample from the operator. The output is 21 bits wide of which the MSB denotes the sign. This function will also update the internal state
	 * of the operator, so that successive calls will each yield the next sample.
	 * @param freqDiff Applies phase modulation to the operator. Used for Feedback and modulator input.
	 * @param vib Also applies phase modulation to the operator, but is handled in a different way then freqDiff. Used for vibrato effect.
	 * @param ampDiff Applies amplitude modulation to the operator. Used for envelope, key level scaling and tremolo effect. Unit is "dB".
	 * @return One sample of the modulator.
	 */
	public int fetchSample(int freqDiff,int vib,int ampDiff) {
		if(phaseInc>0) {
			phaseInc=phaseInc+0;
		}
		int realPhase=((phase>>2)+freqDiff) & IN_MASK;
		phase=(phase+phaseInc*mult+vib*mult) & IN_MASK;
		
		//Check if the output should be negated (phase>pi)
		boolean negate=(realPhase & (1<<17))!=0;
		
		//Get value in "dB"
		int output=logSin(realPhase);
		
		output+=ampDiff;
		
		//Convert to linear
		output=fastExpTable[output];
		
		if(negate) {
			if(rectify) {	//set output to 0 if half sine waveform is selected
				output=0;
			}else {		//otherwise, just negate output
				output=-output;
			}
		}
		
		//Average with previous value
		int temp=prevOutput;
		prevOutput=output;
		output=(output+temp)>>1;
		return output;
	}
}
