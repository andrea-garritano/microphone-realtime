package com.garritano.microphonerealtime;



import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;

public class GraphActivity extends Activity {
	
	private static final int RECORDER_SAMPLERATE = 8000;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	private AudioRecord recorder = null;
	private Thread recordingThread = null;
	private boolean isRecording = false;
	private boolean newSound = false;
	
	//Costanti di campionamento
	int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
	int N = 512;
	int BytesPerElement = 2; // 2 bytes in 16bit format
	
	//questa serve a dare una scala fissa allo spettro finale
	int SCALE = 50;
	
	short sData[] = new short[BufferElements2Rec];
	
	//Costanti dell'ampiezza spettrale
    double[] C = new double[N];
    
    //Matrici di seni e coseni, servono a ridurre la quantità di calcolo realtime
    //Forma: Funzione[k][t]
    double [][] Sin = new double [N][BufferElements2Rec];
    double [][] Cos = new double [N][BufferElements2Rec];
	
	
	
    private final Handler mHandler = new Handler();
    private Runnable mTimer1;
    private GraphView graphView;
    private GraphViewSeries exampleSeries;

    

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//inizializza le matrici di seno e coseno.
		initSinCos();
    	
		setContentView(R.layout.activity_graph);
		graphView = new LineGraphView(
                this // context
                , "GraphViewDemo" // heading
        );
		
		exampleSeries = new GraphViewSeries(new GraphViewData[] {});

		graphView.addSeries(exampleSeries);
		LinearLayout layout = (LinearLayout) findViewById(R.id.graph1);
		layout.addView(graphView);
		startRecording();
	}
	
	@Override
    protected void onPause() {
        pauseRecording();
        mHandler.removeCallbacks(mTimer1);
        super.onPause();
    }
	
	@Override
    protected void onDestroy() {
        stopRecording();
        mHandler.removeCallbacks(mTimer1);
        super.onDestroy();
    }
	
	@Override
    protected void onResume() {
        super.onResume();
        isRecording = true;
        recorder.startRecording();
        
    	newSound = true;
    	//thread che aggiorna il grafico
        mTimer1 = new Runnable() {
            @Override
            public void run() {
            	
            	//se  ci sono nuovi dati aggiorna il grafico
            	if(newSound)
            	{
	                GraphViewData[] datatoPrint;
	                
	                //fa iniziare il nuovo campionamento
	                newSound=false;
	                //avvia la FFT intanto che campiona il nuovo segnale
	                datatoPrint=DFT();
	                
	                //Aggiorna il grafico
	                exampleSeries.resetData(datatoPrint);
	                
            	}
                //chiama ricorsivamente questa stessa funzione
                mHandler.postDelayed(this, 0);
            }
        };
        mHandler.postDelayed(mTimer1, 500);        
    }
	
	
	
	private void startRecording() {
	    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
	            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
	            RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
	
	    recorder.startRecording();
	    isRecording = true;
	    
	    //avvia il thread che salva i dati
	    recordingThread = new Thread(new Runnable() {
	        public void run() {
	            saveAudioDataToArray();
	        }
	    }, "AudioRecorder Thread");
	    recordingThread.start();
	}
	
	
	
	//Registra i dati dentro sData
	private void saveAudioDataToArray() {
	
	    while (isRecording) {
	    	while (!newSound){}
		    recorder.read(sData, 0, BufferElements2Rec);
		    newSound=true;
	    }
	   
	}
	
	private GraphViewData[] DFT(){
		
		GraphViewData[] datatoPrint= new GraphViewData[N];
		
		//Elabora lo spettro d'ampiezza
        //variabili impiegate
       
        
        //elaborazione dello spettro
        for (int k = 0; k < N; k++){
        	
        	C[k] = 0;
        	double X = 0; //parte reale
        	double Y = 0; //parte immaginaria
        	
        	//Sommatoria del prodotto tra segnale registrato e seni-coseni
        	for (int n=0; n<BufferElements2Rec; n++){
        		X = X + ( sData[n] * Cos[k][n] );
        		Y = Y + ( sData[n] * Sin[k][n] );
        	}
        	
        	
        	//modulo del numero complesso messo in scala logaritmica
        	//probabilmente questo è il calcolo più pesante
        	C[k] = 10*Math.log10( Math.sqrt(X*X+Y*Y) );
        	
        	//Questa è la funzione per vedere la fase. È divertente.
        	//C[k] = Math.atan2(Y, X);
        	
        	//toglie la media del segnale
        	if (k!=0) C[k]=C[k]-C[0];
        	
        	//converte nel formato giusto
        	//in questa fase, contemporaneamente, converte il valore K nella giusta frequenza
        	datatoPrint[k] = new GraphViewData(RECORDER_SAMPLERATE*(k+1)/BufferElements2Rec, C[k]);
        }
        
        //mette una soglia accettabile per la scala
        datatoPrint [0] = new GraphViewData(0, SCALE);
        datatoPrint [1] = new GraphViewData(0, -SCALE);
		
        
		return datatoPrint;
	}
	
	private void pauseRecording() {
		if (null != recorder) {
	        isRecording = false;
	        recorder.stop();
		}
	}
	
	private void stopRecording() {
	    // stops the recording activity
	    if (null != recorder) {
	        isRecording = false;
	        recorder.stop();
	        recorder.release();
	        recorder = null;
	        recordingThread = null;
	    }
	}
	
	private void initSinCos(){
		double alpha = (double) (2 * Math.PI / BufferElements2Rec); //così moltiplica una volta sola.
		for (int k=0; k<N; k++){
			double beta = (double) (alpha*k);
			for (int t=0; t<BufferElements2Rec; t++){
				Cos[k][t]=Math.cos(beta*t);
				Sin[k][t]=Math.sin(beta*t);
			}
		}
	}

}
