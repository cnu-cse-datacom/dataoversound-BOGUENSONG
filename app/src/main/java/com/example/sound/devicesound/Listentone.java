package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;

    ArrayList<Integer> bit_chunks = new ArrayList<Integer>();


    public Listentone() {

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();

    }

    private int findPowerSize(int round) {

        int square = 2 ; //가장 가까운 제곱수를 저장할 변수
        //Log.d("ListenToneWord", "round: "+ round);
        while(true)
        {
            if (square <= round )
            {

                square *= 2;
                if (round < square)
                {
                    square = square/2;
                    break;
                }
            }
            else
            {
                break;
            }
        }
        return square;
    }


    private int findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];


        Complex[] complex = transform.transform(toTransform, TransformType.FORWARD);


        for (int i = 0; i < complex.length; i++) {
            realNum = complex[i].getReal();
            imgNum = complex[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
        }

        Double[] freq = this.fftfreq(complex.length,1);


        double findMax=mag[0];
        int findMax_index=0;
        for (int i = 0; i < complex.length; i++) {

            if (findMax < mag[i]) {
                findMax = mag[i];
                findMax_index = i;
            }
        }


        double peak_freq=freq[findMax_index];

        return Math.abs((int)(mSampleRate*peak_freq));


    }


    private Double[] fftfreq(int n, int d) {
        double val = 1.0 / (n * d);
        Double[] results = new Double[n];
        int N = (n - 1)/ 2 + 1;
        int[] p = new int[n];
        for(int i = 0; i < N; i++) {
            p[i] = i;
        }
        for(int i = N, j = -(n/2); i < n; i++,j++){
            p[i] = j;
        }
        for(int i = 0; i < n; i++){
            results[i] = p[i] * val;
        }
        return results;
    }

    private ArrayList<Integer> extract_packet(ArrayList<Integer> packet) {

        ArrayList<Integer> freqs=new ArrayList<Integer>();
        for(int t=0; t<(packet.size()/2)-1; t++){

            freqs.add(packet.get((t*2)+2));
        }

        for(int i=0; i<freqs.size(); i++) {
            int temp=((int)(Math.round((freqs.get(i)-START_HZ)/STEP_HZ)));


            if((0<=temp)&&(temp<16)) {
                bit_chunks.add(temp);

            }
        }

        ArrayList<Integer> out_bytes=new ArrayList<Integer>();

        int next_read_chunk = 0;
        int next_read_bit = 0;

        int byte_temp = 0;
        int bits_left = 8;
        while (next_read_chunk < bit_chunks.size()) {
            int can_fill = BITS - next_read_bit;
            int to_fill = (bits_left>can_fill)? can_fill:bits_left ;
            int offset = BITS - next_read_bit - to_fill;
            byte_temp <<=to_fill;
            int shifted = bit_chunks.get(next_read_chunk) & (((1 << to_fill) - 1) << offset);
            byte_temp |=shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;
            if (bits_left <= 0) {
                out_bytes.add(byte_temp);
                byte_temp = 0;
                bits_left = 8;
            }
            if (next_read_bit >= BITS) {
                next_read_chunk += 1;
                next_read_bit -= BITS;
            }
        }

        bit_chunks.clear();
        return out_bytes;
    }


    public void PreRequest() {

        int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        //Log.d("ListenToneWord", "block size: "+ blocksize);
        short[] buffer = new short[blocksize];
        int bufferedReadResult = mAudioRecord.read(buffer, 0,blocksize);

        boolean in_packet = false;
        ArrayList<Integer> packet= new ArrayList<Integer>();
        double[] chunk= new double[blocksize];
        int dom=0;

        ArrayList<Integer> out_bytes_display=new ArrayList<Integer>(); //


        while (true) {

            bufferedReadResult = mAudioRecord.read(buffer,0,blocksize);


            if(bufferedReadResult<0){
                continue;
            }

            for(int t=0; t<blocksize; t++){
                double temp=buffer[t];
                chunk[t]=temp;
            }

            dom = this.findFrequency(chunk);
            if (in_packet && ((dom>=HANDSHAKE_END_HZ-20)&&(dom<=HANDSHAKE_END_HZ+20))) {
                out_bytes_display = extract_packet(packet);

                String to_print= "";
                for(int t=0; t<out_bytes_display.size(); t++) {
                    int char_temp=out_bytes_display.get(t);

                    Log.d("ListenToneWord", t+": "+(char) char_temp);
                    to_print=to_print+((char) char_temp);
                }
                out_bytes_display.clear();
                Log.d("ListenToneWord", to_print);
                packet.clear();
                in_packet = false;
            }
            else if(in_packet) {
                packet.add(dom);
            }
            else if ((dom>=HANDSHAKE_START_HZ-20)&&(dom<=HANDSHAKE_START_HZ+20)) {
                in_packet = true;
            }



        }





    }

}
