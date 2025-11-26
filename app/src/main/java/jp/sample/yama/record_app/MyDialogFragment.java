package jp.sample.yama.record_app;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import jp.sample.yama.R;


/**
 * アプリ内で使用するシンプルなダイアログ表示用の DialogFragment。
 *
 * このフラグメントは `MaterialAlertDialogBuilder` を使ってタイトルと本文を
 * 表示するだけの簡単なダイアログを作成します。アクティビティから
 * `new MyDialogFragment().show(...)` のように呼び出して使用します。
 */
public class MyDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 現在のアクティビティを取得して Material デザインの AlertDialog を構築する
        // タイトルとメッセージはリソースから読み込みます（`R.string.button_usage`, `R.string.usage_body`）
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.button_usage)
                .setMessage(R.string.usage_body)
                .create();
    }
}
