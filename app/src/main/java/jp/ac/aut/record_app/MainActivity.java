package jp.ac.aut.record_app;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    // UI のボタン
    Button recording_button;
    Button playback_button;

    // 録音中フラグ（true: 録音中）
    Boolean recording_flag = false;


    // オーディオ録音に関するフィールド
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private byte[] audioData; // 生PCMバイトを格納するバッファ
    private int bufferSize;   // AudioRecord が返す最小バッファサイズ

    private static final int PERMISSION_REQUEST_CODE = 1;

    private android.graphics.Color Color;
    // 録音中／待機時に使用する色。変更を容易にするため集中管理しています。
    private final int COLOR_RECORDING = Color.parseColor("#FFE60033");
    private final int COLOR_IDLE = Color.parseColor("#FF6750A3");

    /**
     * アクティビティ作成時：UI・バッファの初期化、権限確認を行います。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // UI 要素をバインド
        recording_button = findViewById(R.id.recording_button);
        playback_button = findViewById(R.id.playback_button);

        // 初期オーディオ設定：44100Hz, モノラル, 16bit PCM
        int sampleRate = 44100;
        bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // 十分大きなバッファを確保（bufferSize * 600） — およそ30秒分を想定
        audioData = new byte[bufferSize * 600]; // 約30秒分（目安）

        // RECORD_AUDIO 権限を確認し、なければリクエストする
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }

        // 録音ボタン：押すたびに録音開始／停止をトグル
        recording_button.setOnClickListener(v -> {
            recording_flag = !recording_flag;

            if (recording_flag) {
                startRecording();
                // 中央管理された UI 更新メソッドを使う
                updateRecordingUi(true);
            } else {
                stopRecording();
                updateRecordingUi(false);
            }
        });

        // 再生ボタン：録音済みの PCM を再生
        playback_button.setOnClickListener(v -> playRecording());

        // 使い方ボタン
        findViewById(R.id.button_usage).setOnClickListener(view -> {
            DialogFragment dialogFragment = new MyDialogFragment();
            dialogFragment.show(getSupportFragmentManager(), "usage");
        });
    }

    /**
     * 録音状態に応じてボタンのテキスト・色・有効状態を一元的に更新します。
     * runOnUiThread を使って UI スレッドで安全に更新します。
     *
     * @param recording true=録音中（停止ボタンを表示、再生無効）
     */
    @SuppressLint("ResourceAsColor")
    private void updateRecordingUi(boolean recording) {
        runOnUiThread(() -> {
            if (recording) {
                recording_button.setText(R.string.stop_record_button);
                recording_button.setBackgroundTintList(ColorStateList.valueOf(COLOR_RECORDING));
                playback_button.setEnabled(false);
            } else {
                recording_button.setText(R.string.start_record_button);
                recording_button.setBackgroundTintList(ColorStateList.valueOf(COLOR_IDLE));
                playback_button.setEnabled(true);
            }
        });
    }

    /**
     * 録音を開始します。バックグラウンドスレッドで AudioRecord から読み取り、
     * `audioData` バッファに追記していきます。ユーザが停止するかバッファが満杯になるまで継続します。
     */
    private void startRecording() {
        // 権限確認：なければ開始できない
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // UI を中央管理で更新（色やボタン無効化など）
        updateRecordingUi(true);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        audioRecord.startRecording();

        // ワーカースレッドで読み取りを行い、audioData に書き込む
        recordingThread = new Thread(() -> {
            int read;
            int offset = 0;
            // 録音フラグが true かつバッファに空きがある間ループ
            while (recording_flag && offset < audioData.length) {
                read = audioRecord.read(audioData, offset, bufferSize);
                if (read > 0) {
                    offset += read;
                }
            }
            // ループ終了後は停止済みまたはバッファが満杯の状態
        });

        recordingThread.start();
    }

    /**
     * 録音を停止し、AudioRecord を解放します。
     */
    private void stopRecording() {
        if (audioRecord != null) {
            // 録音フラグを折り、リソースを解放
            recording_flag = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
            // 停止後の UI 更新（再生ボタンを再び有効にする等）
            updateRecordingUi(false);
        }
    }

    /**
     * `audioData` バッファの内容を AudioTrack で再生します。データがない場合は何もしません。
     */
    private void playRecording() {
        if (audioData == null) return;

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioData.length,
                AudioTrack.MODE_STATIC);

        // バッファ全体を書き込み、再生を開始
        audioTrack.write(audioData, 0, audioData.length);
        audioTrack.play();
    }

    /**
     * 権限リクエストの結果を処理します。RECORD_AUDIO が拒否された場合は
     * 録音・再生関連の UI を無効化します。
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // 権限が拒否された場合、録音・再生ボタンを無効にする
                recording_button.setEnabled(false);
                playback_button.setEnabled(false);
            }
        }
    }
}