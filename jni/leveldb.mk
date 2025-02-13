LOCAL_PATH := $(ROOT_PATH)/leveldb

include $(CLEAR_VARS)
LOCAL_MODULE := leveldb #1.18
LOCAL_CFLAGS := -D_REENTRANT -DOS_ANDROID -DLEVELDB_PLATFORM_POSIX -DNDEBUG

ifneq ($(SNAPPY),)
LOCAL_CFLAGS += -DSNAPPY
LOCAL_STATIC_LIBRARIES += snappy
endif

ifeq ($(TARGET_ARCH_ABI), arm64-v8a)
LOCAL_CFLAGS += -D__ARMEL__
endif

ifneq ($(filter $(TARGET_ARCH_ABI), mips mips64),)
LOCAL_CFLAGS += -DARCH_CPU_X86_FAMILY=1
endif

LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
    db/builder.cc \
    db/c.cc \
    db/db_impl.cc \
    db/db_iter.cc \
    db/dbformat.cc \
    db/filename.cc \
    db/log_reader.cc \
    db/log_writer.cc \
    db/memtable.cc \
    db/repair.cc \
    db/table_cache.cc \
    db/version_edit.cc \
    db/version_set.cc \
    db/write_batch.cc \
    table/block.cc \
    table/block_builder.cc \
    table/filter_block.cc \
    table/format.cc \
    table/iterator.cc \
    table/merger.cc \
    table/table.cc \
    table/table_builder.cc \
    table/two_level_iterator.cc \
    util/arena.cc \
    util/bloom.cc \
    util/cache.cc \
    util/coding.cc \
    util/comparator.cc \
    util/crc32c.cc \
    util/env.cc \
    util/env_posix.cc \
    util/filter_policy.cc \
    util/hash.cc \
    util/histogram.cc \
    util/logging.cc \
    util/options.cc \
    util/status.cc \
    port/port_posix.cc

include $(BUILD_STATIC_LIBRARY)
