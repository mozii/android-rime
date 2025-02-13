/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.res.Configuration;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.Window;
import android.view.WindowManager;
import android.app.AlertDialog;

import java.util.logging.Logger;

/**
 * Abstract class extended by all Dialect IME.
 */
public class Trime extends InputMethodService implements 
    KeyboardView.OnKeyboardActionListener, CandView.CandViewListener {

  protected KeyboardView inputView;
  private CandContainer candidatesContainer;
  private KeyboardSwitch keyboardSwitch;
  private Pref mPref;
  private Effect effect;
  private int orientation;

  private boolean canCompose;
  private boolean enterAsLineBreak;
  private boolean isLeftApo = true;
  private boolean isLeftQuote = true;

  private AlertDialog mOptionsDialog;
  private static Trime self;
  private Rime mRime;
  private static Logger Log = Logger.getLogger(Trime.class.getSimpleName());

  @Override
  public void onCreate() {
    super.onCreate();
    self = this;
    mPref = new Pref(this);
    effect = new Effect(this);
    keyboardSwitch = new KeyboardSwitch(this);
    keyboardSwitch.init();
    mRime = Rime.getRime();

    orientation = getResources().getConfiguration().orientation;
    // Use the following line to debug IME service.
    //android.os.Debug.waitForDebugger();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    self = null;
    mRime.destroy();
  }

  public static Trime getService() {
    return self;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (orientation != newConfig.orientation) {
      // Clear composing text and candidates for orientation change.
      escape();
      orientation = newConfig.orientation;
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
      int newSelEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, 
        candidatesStart, candidatesEnd);
    if ((candidatesEnd != -1) &&
        ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
      // Clear composing text and its candidates for cursor movement.
      escape();
    }
    // Update the caps-lock status for the current cursor position.
    //updateCursorCapsToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    inputView = (KeyboardView) getLayoutInflater().inflate(
        R.layout.input, null);
    inputView.setOnKeyboardActionListener(this);
    return inputView;
  }

  @Override
  public View onCreateCandidatesView() {
    candidatesContainer = (CandContainer) getLayoutInflater().inflate(
        R.layout.candidates, null);
    candidatesContainer.setCandViewListener(this);
    return candidatesContainer;
  }
    
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
    editorstart(attribute.inputType);
    effect.reset();
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    bindKeyboardToInputView();
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    // Dismiss any pop-ups when the input-view is being finished and hidden.
    inputView.closing();
    escape();
  }


  private void bindKeyboardToInputView() {
    if (inputView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = (Keyboard)keyboardSwitch.getCurrentKeyboard();
      int i = mPref.getKeyTextSize();
      inputView.setTextSize(i);
      inputView.setKeyboard(sk);
      inputView.setPreviewEnabled(mPref.isKeyboardPreview());
      //updateCursorCapsToInputView();
    }
  }

  /**
   * Resets the internal state of this editor, typically called when a new input
   * session commences.
   */
  private void editorstart(int inputType) {
    canCompose = false;
    enterAsLineBreak = false;

    switch (inputType & InputType.TYPE_MASK_CLASS) {
      case InputType.TYPE_CLASS_TEXT:
        canCompose = true;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
          // Make enter-key as line-breaks for messaging.
          enterAsLineBreak = true;
        }
        break;
    }
    mRime = Rime.getRime();
    // Select a keyboard based on the input type of the editing field.
    keyboardSwitch.init(getMaxWidth());
    keyboardSwitch.onStartInput(inputType);
    setCandidatesViewShown(true);
    escape();
    setCandidatesViewShown(false);
  }

  private boolean isComposing() {
    return mRime.isComposing();
  }

  /**
   * Commits the given text to the editing field.
   */
  private void commitText(CharSequence text) {
    if (text == null) return;
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      if (text.length() > 1) {
        // Batch edit a sequence of characters.
        ic.beginBatchEdit();
        ic.commitText(text, 1);
        ic.endBatchEdit();
      } else {
        ic.commitText(text, 1);
      }
    }
    if (!isComposing()) mRime.commitComposition(); //自動上屏
  }

  private boolean commitText() { //Rime commit text
    boolean r = mRime.getCommit();
    if (r) commitText(mRime.getCommitText());
    return r;
  }

  private CharSequence getLastText() {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.getTextBeforeCursor(1,0);
    }
    return "";
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (canCompose && onEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  private boolean onEvent(KeyEvent event) { //實體鍵盤
    Log.info("onEvent="+event);
    int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
        keyCode == KeyEvent.KEYCODE_HOME ||
        keyCode == KeyEvent.KEYCODE_MENU ||
        keyCode == KeyEvent.KEYCODE_SEARCH) return false;
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      escape(); //返回鍵清屏
      return false;
    }
    if (!isComposing()) {
      if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ENTER)
      return false;
    }
    onKey(event.getKeyCode(), event.getModifiers());
    return true;
  }

  public void onKey(int primaryCode, int mask) { //軟鍵盤
    Log.info("onKey="+primaryCode);
    if (keyboardSwitch.onKey(primaryCode)) {
      Log.info("keyboardSwitch onKey");
      bindKeyboardToInputView();
      escape();
    } else if (primaryCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
      mRime.toggleOption("ascii_mode");
      commitText();
      updateComposing();
    } else if(mRime.onKey(Keyboard.getRimeKeyEvent(primaryCode, mask))) {
      Log.info("Rime onKey");
      commitText();
      updateComposing();
    } else if (handleOption(primaryCode) || handleEnter(primaryCode)) {
      Log.info("Trime onKey");
    } else {
      Log.info("send Key");
      sendDownUpKeyEvents(primaryCode);
    }
  }

  public void onText(CharSequence text) { //軟鍵盤
    Log.info("onText="+text);
    mRime.onText(text);
    if(!commitText() && !isComposing()) commitText(text);
    updateComposing();
  }

  public void onPress(int primaryCode) {
    effect.vibrate();
    effect.playSound(primaryCode);
  }

  public void onRelease(int primaryCode) {
    // no-op
  }

  public void swipeLeft() {
    // no-op
  }

  public void swipeRight() {
    // no-op
  }

  public void swipeUp() {
    // no-op
  }

  public void swipeDown() {
    // requestHideSelf(0); //隱藏輸入窗
  }

  public void onPickCandidate(int i) {
    // Commit the picked candidate and suggest its following words.
    if (!isComposing()) {
      if (i >=0) {
        mRime.toggleOption(i);
        updateComposing();
      }
    } else if (i == -4) onKey(KeyEvent.KEYCODE_PAGE_UP, 0);
    else if (i == -5) onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0);
    else if (mRime.selectCandidate(i)) {
      commitText();
      updateComposing();
    }
  }

  private void updateComposing() {
    if (mPref.isEmbedFirst()) { //嵌入首選
      InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        // Set cursor position 1 to advance the cursor to the text end.
        ic.setComposingText(mRime.getComposingText(), 1);
      }
    }
    if (candidatesContainer != null) {
      candidatesContainer.updatePage();
      setCandidatesViewShown(canCompose);
    }
  }

  private boolean handleOption(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
        // Create a Dialog menu
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setTitle(R.string.ime_name)
        //.setIcon(android.R.drawable.ic_menu_preferences)
        .setCancelable(true)
        .setSingleChoiceItems(mRime.get_schema_names(), mRime.getCurrentSchemaId(),
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface di, int id) {
            di.dismiss();
            if (mRime.selectSchema(id)) {
              escape();
              keyboardSwitch.init();
              bindKeyboardToInputView();
            }
          }
        })
        .setNegativeButton(R.string.other_ime, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int id) {
                di.dismiss();
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
            }
        })
        .setPositiveButton(R.string.set_ime, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int id) {
                di.dismiss();
                Intent iSetting = new Intent();
                iSetting.setClass(Trime.this, PrefActivity.class);
                iSetting.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(iSetting);
                escape(); //全局設置時清屏
            }
        });
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (candidatesContainer != null) lp.token = candidatesContainer.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
        return true;
    }
    return false;
  }

  private boolean handleEnter(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      if (enterAsLineBreak) {
        commitText("\n");
      } else {
        sendKeyChar('\n');
      }
      return true;
    }
    return false;
  }

  /**
   * Simulates PC Esc-key function by clearing all composing-text or candidates.
   */
  private void escape() {
    if (isComposing()) {
      mRime.clearComposition();
      updateComposing();
    }
  }
}
