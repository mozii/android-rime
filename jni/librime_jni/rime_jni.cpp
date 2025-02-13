#include <jni.h>
#include <string.h>
#include <rime_api.h>
#include <rime/key_table.h>
#define LOG_TAG "Rime-JNI"

#ifdef ANDROID
#include <android/log.h>
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)
#else
#include <stdio.h>
#define ALOGE printf
#endif

template <typename T, int N>
char (&ArraySizeHelper(T (&array)[N]))[N];
#define NELEMS(x) (sizeof(ArraySizeHelper(x)))

void on_message(void* context_object,
                RimeSessionId session_id,
                const char* message_type,
                const char* message_value) {
  ALOGE("message: [%lu] [%s] %s\n", session_id, message_type, message_value);
}

jstring newJstring(JNIEnv* env, const char* pat)
{
  jclass strClass = env->FindClass("java/lang/String");
  jmethodID ctorID = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
  if (!pat) return NULL;
  int n = strlen(pat);
  if (n == 0) return NULL;
  jbyteArray bytes = env->NewByteArray(n);
  env->SetByteArrayRegion(bytes, 0, n, (jbyte*)pat);
  jstring encoding = env->NewStringUTF("utf-8");
  return (jstring)env->NewObject(strClass, ctorID, bytes, encoding);
}

static void start(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir) {
  RIME_STRUCT(RimeTraits, traits);
  
  const char* str1 = shared_data_dir == NULL ? NULL : env->GetStringUTFChars(shared_data_dir, NULL); 
  const char* str2 = user_data_dir == NULL ? NULL : env->GetStringUTFChars(user_data_dir, NULL); 
  if (str1 != NULL) traits.shared_data_dir = str1;
  if (str2 != NULL) traits.user_data_dir = str2;
  traits.app_name = "rime.jni";
  ALOGE("setup...\n");
  RimeInitialize(&traits);
  env->ReleaseStringUTFChars(shared_data_dir, str1);
  env->ReleaseStringUTFChars(user_data_dir, str2);
}

static void set_notification_handler(JNIEnv *env, jobject thiz) { //TODO
  RimeSetNotificationHandler(&on_message, NULL);
}

static void check(JNIEnv *env, jobject thiz, jboolean full_check) {
  RimeStartMaintenance((Bool)full_check);
  if (RimeIsMaintenancing()) RimeJoinMaintenanceThread();
  RimeSetNotificationHandler(&on_message, NULL);
}

// entry and exit
static void finalize(JNIEnv *env, jobject thiz) {
  ALOGE("finalize...");
  RimeFinalize();
}

// deployment
static jboolean sync_user_data(JNIEnv *env, jobject thiz) {
  ALOGE("sync user data...");
  return RimeSyncUserData();
}

// session management
static jint create_session(JNIEnv *env, jobject thiz) {
  RimeSessionId session_id = RimeCreateSession();
  RimeSetOption(session_id, "soft_cursor", True);
  return session_id;
}

static jboolean find_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeFindSession((RimeSessionId)session_id);
}

static jboolean destroy_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeDestroySession((RimeSessionId)session_id);
}

static void cleanup_stale_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupStaleSessions();
}

static void cleanup_all_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupAllSessions();
}

// input
static jboolean process_key(JNIEnv *env, jobject thiz, jint session_id, jint keycode, jint mask) {
  return RimeProcessKey((RimeSessionId)session_id, keycode, mask);
}

static jboolean commit_composition(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeCommitComposition((RimeSessionId)session_id);
}

static void clear_composition(JNIEnv *env, jobject thiz, jint session_id) {
  RimeClearComposition((RimeSessionId)session_id);
}

// output
static jboolean get_commit(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeCommit, commit);
  Bool r = RimeGetCommit((RimeSessionId)session_id, &commit);
  jclass jc = env->GetObjectClass(thiz);
  jfieldID fid = env->GetFieldID(jc, "commit_text", "Ljava/lang/String;");
  jstring s = NULL;
  if (r) {
    s = newJstring(env, commit.text);
    RimeFreeCommit(&commit);
  }
  env->SetObjectField(thiz, fid, s);
  return r;
}

static jboolean get_status(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeStatus, status);
  Bool r = RimeGetStatus(session_id, &status);
  if (r) {
    jclass jc = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(jc, "schema_id", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, newJstring(env, status.schema_id));
    fid = env->GetFieldID(jc, "schema_name", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, newJstring(env, status.schema_name));
    fid = env->GetFieldID(jc, "is_disabled", "Z");
    env->SetBooleanField(thiz, fid, status.is_disabled);
    fid = env->GetFieldID(jc, "is_composing", "Z");
    env->SetBooleanField(thiz, fid, status.is_composing);
    fid = env->GetFieldID(jc, "is_ascii_mode", "Z");
    env->SetBooleanField(thiz, fid, status.is_ascii_mode);
    fid = env->GetFieldID(jc, "is_full_shape", "Z");
    env->SetBooleanField(thiz, fid, status.is_full_shape);
    fid = env->GetFieldID(jc, "is_simplified", "Z");
    env->SetBooleanField(thiz, fid, status.is_simplified);
    fid = env->GetFieldID(jc, "is_traditional", "Z");
    env->SetBooleanField(thiz, fid, status.is_traditional);
    fid = env->GetFieldID(jc, "is_ascii_punct", "Z");
    env->SetBooleanField(thiz, fid, status.is_ascii_punct);
    RimeFreeStatus(&status);
  }
  return r;
}

static jboolean get_context(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeContext, context);
  Bool r = RimeGetContext(session_id, &context);
  if (r) {
    jclass jc = env->GetObjectClass(thiz);
    jfieldID fid;
    fid = env->GetFieldID(jc, "commit_text_preview", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, newJstring(env, context.commit_text_preview));

    fid = env->GetFieldID(jc, "menu_num_candidates", "I");
    env->SetIntField(thiz, fid, context.menu.num_candidates);
    fid = env->GetFieldID(jc, "menu_page_size", "I");
    env->SetIntField(thiz, fid, context.menu.page_size);
    fid = env->GetFieldID(jc, "menu_page_no", "I");
    env->SetIntField(thiz, fid, context.menu.page_no);
    fid = env->GetFieldID(jc, "menu_highlighted_candidate_index", "I");
    env->SetIntField(thiz, fid, context.menu.highlighted_candidate_index);
    fid = env->GetFieldID(jc, "menu_is_last_page", "Z");
    env->SetBooleanField(thiz, fid, context.menu.is_last_page);
    fid = env->GetFieldID(jc, "menu_select_keys", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, newJstring(env, context.menu.select_keys));

    fid = env->GetFieldID(jc, "composition_length", "I");
    env->SetIntField(thiz, fid, context.composition.length);
    fid = env->GetFieldID(jc, "composition_cursor_pos", "I");
    env->SetIntField(thiz, fid, context.composition.cursor_pos);
    fid = env->GetFieldID(jc, "composition_sel_start", "I");
    env->SetIntField(thiz, fid, context.composition.sel_start);
    fid = env->GetFieldID(jc, "composition_sel_end", "I");
    env->SetIntField(thiz, fid, context.composition.sel_end);
    fid = env->GetFieldID(jc, "composition_preedit", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, newJstring(env, context.composition.preedit));

    int n = context.menu.num_candidates;
    fid = env->GetFieldID(jc, "candidates_text", "[Ljava/lang/String;");
    jobjectArray texts = (jobjectArray) env->GetObjectField(thiz, fid);
    fid = env->GetFieldID(jc, "candidates_comment", "[Ljava/lang/String;");
    jobjectArray comments = (jobjectArray) env->GetObjectField(thiz, fid);
    for (int i = 0; i < n;  ++i) {
      env->SetObjectArrayElement(texts, i, newJstring(env, context.menu.candidates[i].text));
      env->SetObjectArrayElement(comments, i, newJstring(env, context.menu.candidates[i].comment));
    }
    RimeFreeContext(&context);
  }
  return r;
}

// runtime options
static void set_option(JNIEnv *env, jobject thiz, jint session_id, jstring option, jboolean value) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  RimeSetOption(session_id, s, value);
  env->ReleaseStringUTFChars(option, s);
}

static jboolean get_option(JNIEnv *env, jobject thiz, jint session_id, jstring option) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  bool value = RimeGetOption(session_id, s);
  env->ReleaseStringUTFChars(option, s);
  return value;
}

static jobjectArray get_schema_names(JNIEnv *env, jobject thiz) {
  RimeSchemaList list;
  bool value =RimeGetSchemaList(&list);
  jobjectArray ret = NULL;
  if (value) {
    int n = list.size;
    ret = (jobjectArray) env->NewObjectArray(n, env->FindClass("java/lang/String"), NULL);
    for (size_t i = 0; i < list.size; ++i) {
      env->SetObjectArrayElement(ret, i, newJstring(env, list.list[i].name));
    }
    RimeFreeSchemaList(&list);
  }
  return ret;
}

static jobjectArray get_schema_ids(JNIEnv *env, jobject thiz) {
  RimeSchemaList list;
  bool value =RimeGetSchemaList(&list);
  jobjectArray ret = NULL;
  if (value) {
    int n = list.size;
    ret = (jobjectArray) env->NewObjectArray(n, env->FindClass("java/lang/String"), NULL);
    for (size_t i = 0; i < list.size; ++i) {
      env->SetObjectArrayElement(ret, i, newJstring(env, list.list[i].schema_id));
    }
    RimeFreeSchemaList(&list);
  }
  return ret;
}

static jstring get_current_schema(JNIEnv *env, jobject thiz, jint session_id) {
  char current[100] = {0};
  bool value = RimeGetCurrentSchema(session_id, current, sizeof(current));
  if (value) return newJstring(env, current);
  return NULL;
}

static jboolean select_schema(JNIEnv *env, jobject thiz, jint session_id, jstring schema_id) {
  const char* s = schema_id == NULL ? NULL : env->GetStringUTFChars(schema_id, NULL);
  bool value = RimeSelectSchema(session_id, s);
  env->ReleaseStringUTFChars(schema_id, s);
  return value;
}

//testing
static jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jint session_id, jstring key_sequence) {
  const char* str = env->GetStringUTFChars(key_sequence, NULL); 
  if (str == NULL) return false; /* OutOfMemoryError already thrown */
  jboolean r = RimeSimulateKeySequence((RimeSessionId)session_id, str);
  env->ReleaseStringUTFChars(key_sequence, str);
  return r;
}

static jboolean select_candidate(JNIEnv *env, jobject thiz, jint session_id, jint index) {
  RimeApi* rime = rime_get_api();
  return rime->select_candidate(session_id, index);
}

static jstring get_version(JNIEnv *env, jobject thiz) {
  RimeApi* rime = rime_get_api();
  const char* c = rime->get_version();
  return newJstring(env, c);
}

static jint get_modifier_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetModifierByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

static jint get_keycode_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetKeycodeByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

static const JNINativeMethod sMethods[] = {
    // init
    {
        const_cast<char *>("start"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(start)
    },
    {
        const_cast<char *>("set_notification_handler"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(set_notification_handler)
    },
    {
        const_cast<char *>("check"),
        const_cast<char *>("(Z)V"),
        reinterpret_cast<void *>(check)
    },
    // entry and exit
    {
        const_cast<char *>("finalize1"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(finalize)
    },
    // deployment
    {
        const_cast<char *>("sync_user_data"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(sync_user_data)
    },
    // session management
    {
        const_cast<char *>("create_session"),
        const_cast<char *>("()I"),
        reinterpret_cast<void *>(create_session)
    },
    {
        const_cast<char *>("find_session"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(find_session)
    },
    {
        const_cast<char *>("destroy_session"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(destroy_session)
    },
    {
        const_cast<char *>("cleanup_stale_sessions"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(cleanup_stale_sessions)
    },
    {
        const_cast<char *>("cleanup_all_sessions"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(cleanup_all_sessions)
    },
    // input
    {
        const_cast<char *>("process_key"),
        const_cast<char *>("(III)Z"),
        reinterpret_cast<void *>(process_key)
    },
    {
        const_cast<char *>("commit_composition"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(commit_composition)
    },
    {
        const_cast<char *>("clear_composition"),
        const_cast<char *>("(I)V"),
        reinterpret_cast<void *>(clear_composition)
    },
    // output
    {
        const_cast<char *>("get_commit"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_commit)
    },
    {
        const_cast<char *>("get_context"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_context)
    },
    {
        const_cast<char *>("get_status"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_status)
    },
    // runtime options
    {
        const_cast<char *>("set_option"),
        const_cast<char *>("(ILjava/lang/String;Z)V"),
        reinterpret_cast<void *>(set_option)
    },
    {
        const_cast<char *>("get_option"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(get_option)
    },
    {
        const_cast<char *>("get_schema_names"),
        const_cast<char *>("()[Ljava/lang/String;"),
        reinterpret_cast<void *>(get_schema_names)
    },
    {
        const_cast<char *>("get_schema_ids"),
        const_cast<char *>("()[Ljava/lang/String;"),
        reinterpret_cast<void *>(get_schema_ids)
    },
    {
        const_cast<char *>("get_current_schema"),
        const_cast<char *>("(I)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_current_schema)
    },
    {
        const_cast<char *>("select_schema"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(select_schema)
    },
    // test
    {
        const_cast<char *>("simulate_key_sequence"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(simulate_key_sequence)
    },
    {
        const_cast<char *>("select_candidate"),
        const_cast<char *>("(II)Z"),
        reinterpret_cast<void *>(select_candidate)
    },
    {
        const_cast<char *>("get_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_version)
    },
    // key_table
    {
        const_cast<char *>("get_modifier_by_name"),
        const_cast<char *>("(Ljava/lang/String;)I"),
        reinterpret_cast<void *>(get_modifier_by_name)
    },
    {
        const_cast<char *>("get_keycode_by_name"),
        const_cast<char *>("(Ljava/lang/String;)I"),
        reinterpret_cast<void *>(get_keycode_by_name)
    },
};

int registerNativeMethods(JNIEnv *env, const char *const className, const JNINativeMethod *methods,
        const int numMethods) {
    jclass clazz = env->FindClass(className);
    if (!clazz) {
        ALOGE("Native registration unable to find class '%s'\n", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) != 0) {
        ALOGE("RegisterNatives failed for '%s'\n", className);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }
    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    // Get jclass with env->FindClass.
    // Register methods with env->RegisterNatives.
    //const char *const kClassPathName = "com/osfans/trime/Rime";
    registerNativeMethods(env, CLASSNAME, sMethods, NELEMS(sMethods));

    return JNI_VERSION_1_6;
}

