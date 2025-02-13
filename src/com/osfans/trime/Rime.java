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
import java.util.logging.Logger;
import java.util.List;
import java.util.Arrays;

public class Rime
{
  private int session_id;
  private static Rime self;
  private static Logger Log = Logger.getLogger(Rime.class.getSimpleName());

  //RimeComposition;
  int composition_length;
  int composition_cursor_pos;
  int composition_sel_start;
  int composition_sel_end;
  String composition_preedit;

  //RimeCandidate
  String[] candidates_text = new String[10];
  String[] candidates_comment = new String[10];

  //RimeMenu
  int menu_page_size;
  int menu_page_no;
  boolean menu_is_last_page;
  int menu_highlighted_candidate_index;
  int menu_num_candidates;
  String menu_select_keys;
  
  //RimeCommit
  String commit_text;
  
  //RimeContext
  String commit_text_preview;

  //RimeStatus
  String schema_id;
  String schema_name;
  boolean is_disabled;
  boolean is_composing;
  boolean is_ascii_mode;
  boolean is_full_shape;
  boolean is_ascii_punct;
  boolean is_simplified;
  boolean is_traditional;
  String[] options = new String[] {
    "ascii_mode",
    "full_shape",
    "ascii_punct"
  };
  String[][] statusTexts= new String[][] {
    {"中文", "西文"},
    {"半角", "全角"},
    {"句讀", "符號"},
  };

  static{
    System.loadLibrary("rime");
    System.loadLibrary("rime_jni");
  }

  public boolean hasLeft() {
    return is_composing && menu_page_no != 0;
  }

  public boolean hasRight() {
    return is_composing && menu_num_candidates != 0 && !menu_is_last_page;
  }

  public boolean isComposing() {
    return is_composing;
  }

  public String getCompositionText() {
    if (composition_length > 0) return composition_preedit;
    else return "";
  }

  public static Rime getRime(){
    if(self == null) self = new Rime();
    return self;
  }

  public String getComposingText() {
    return commit_text_preview != null ? commit_text_preview : "";
  }

  public Rime() {
    init(true);
  }

  public boolean[] getStatus() {
    get_status(session_id);
    return new boolean[] {is_ascii_mode, is_full_shape, is_ascii_punct};
  }

  public String[] getStatusTexts() {
    boolean[] list = getStatus();
    int n = list.length;
    String[] s = new String[n];
    for (int i = 0; i < n; i++) s[i] = statusTexts[i][list[i] ? 1 : 0];
    return s;
  }

  public String[] getStatusComments() {
    boolean[] list = getStatus();
    int n = list.length;
    String[] s = new String[n];
    for (int i = 0; i < n; i++) s[i] = statusTexts[i][list[i] ? 0 : 1];
    return s;
  }

  public void init(boolean full_check) {
    start("/sdcard/rime", "/sdcard/rime");
    check(full_check);
    createSession();
    if (session_id == 0) Log.severe( "Error creating rime session");
    get_status(session_id);
    Log.info("schema_name = " + schema_name + ",schema_id=" + schema_id);
  }

  public void destroy() {
    destroySession();
    finalize1();
    self = null;
  }

  public void createSession() {
    if (session_id == 0 || !find_session(session_id)) session_id = create_session();
  }

  public void destroySession() {
    if (session_id != 0) {
      destroy_session(session_id);
      session_id = 0;
    }
  }

  public String getCommitText() {
    return commit_text;
  }

  public boolean getCommit() {
    boolean b = get_commit(session_id);
    Log.info( "b="+b+",commit="+commit_text);
    return b;
  }

  public boolean getContexts() {
    boolean b = get_context(session_id);
    getStatus();
    Log.info( "compose="+is_composing+",preview="+commit_text_preview);
    return b;
  }

  public boolean onKey(int keycode, int mask) {
    boolean b = process_key(session_id, keycode, mask);
    Log.info( "b="+b+",keycode="+keycode);
    getContexts();
    return b;
  }

  public boolean onKey(int[] event) {
    if (event != null && event.length == 2) return onKey(event[0], event[1]);
    return false;
  }

  public boolean onText(CharSequence text) {
    if(text == null || text.length() == 0) return false;
    boolean b = simulate_key_sequence(session_id, text.toString());
    Log.info( "b="+b+",input="+text);
    getContexts();
    return b;
  }

  public int getCandNum() {
    return menu_num_candidates;
  }

  public String[] getCandidates() {
    if (menu_num_candidates == 0) return null;
    String[] r = new String[menu_num_candidates];
    for(int i = 0; i < menu_num_candidates; i++) r[i] = candidates_text[i];
    return r;
  }

  public String[] getComments() {
    if (menu_num_candidates == 0) return null;
    String[] r = new String[menu_num_candidates];
    for(int i = 0; i < menu_num_candidates; i++) r[i] = candidates_comment[i];
    return r;
  }

  public String getCandidate(int i) {
    if (menu_num_candidates == 0) return null;
    return candidates_text[i];
  }

  public int getCandHighlightIndex() {
    return menu_highlighted_candidate_index;
  }

  public String getComment(int i) {
    if (menu_num_candidates == 0) return null;
    return candidates_comment[i];
  }

  public boolean commitComposition() {
    boolean b = commit_composition(session_id);
    Log.info("commitComposition");
    getContexts();
    return b;
  }

  public void clearComposition() {
    clear_composition(session_id);
    Log.info("clearComposition");
    getContexts();
  }

  public boolean selectCandidate(int index) {
    index += menu_page_no * menu_page_size; //從頭開始
    boolean b = select_candidate(session_id, index);
    Log.info("selectCandidate");
    getContexts();
    return b;
  }

  public void setOption(String option, boolean value) {
    set_option(session_id, option, value);
  }

  public boolean getOption(String option) {
    return get_option(session_id, option);
  }

  public void toggleOption(String option) {
    boolean r = getOption(option);
    setOption(option, !r);
  }

  public void toggleOption(int i) {
    if (i >= 0 && i < options.length) {
      String option = options[i];
      boolean r = getOption(option);
      setOption(option, !r);
    }
  }
  public String getCurrentSchema() {
    schema_id = get_current_schema(session_id);
    return schema_id;
  }

  public int getCurrentSchemaId() {
    schema_id = get_current_schema(session_id);
    List<String> schemas = Arrays.asList(get_schema_ids());
    return schemas.indexOf(schema_id);
  }

  public boolean selectSchema(String schema_id) {
    return select_schema(session_id, schema_id);
  }

  public boolean selectSchema(int id) {
    List<String> schemas = Arrays.asList(get_schema_ids());
    if (schemas.indexOf(schema_id) == id) return false;
    return select_schema(session_id, schemas.get(id));
  }

  // init
  public native final int get_api();
  public native final void set_notification_handler();
  public native final void start(String shared_data_dir, String user_data_dir);
  public native final void check(boolean full_check);
  public native final void finalize1();

  // deployment
  public native final boolean sync_user_data();

  // session management
  public native final int create_session();
  public native final boolean find_session(int session_id);
  public native final boolean destroy_session(int session_id);
  public native final void cleanup_stale_sessions();
  public native final void cleanup_all_sessions();

  // input
  public native final boolean process_key(int session_id, int keycode, int mask);
  public native final boolean commit_composition(int session_id);
  public native final void clear_composition(int session_id);

  // output
  public native final boolean get_commit(int session_id);
  public native final boolean get_context(int session_id);
  public native final boolean get_status(int session_id);

  // runtime options
  public native final void set_option(int session_id, String option, boolean value);
  public native final boolean get_option(int session_id, String option);
  public native final String[] get_schema_names();
  public native final String[] get_schema_ids();
  public native final String get_current_schema(int session_id);
  public native final boolean select_schema(int session_id, String schema_id);

  // testing
  public native final boolean simulate_key_sequence(int session_id, String key_sequence);

  public native final boolean select_candidate(int session_id, int index);
  public static native final String get_version();

  // key_table
  public static native final int get_modifier_by_name(String name);
  public static native final int get_keycode_by_name(String name);
}
