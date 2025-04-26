# 检测操作系统类型
UNAME_S := $(shell uname -s)

# 默认工具链和库
CC=clang
CXX=clang++
AR=ar
LIBS = -lc

# 判断操作系统
ifeq ($(UNAME_S), Darwin)  # macOS
  OS := macos
  CC = clang
  CXX = clang++
  AR = ar
  LIBS = -lc
  # 可选的 macOS 特定编译选项
  CFLAGS += -D_MACOS
  LDFLAGS += -framework Cocoa
endif

ifeq ($(UNAME_S), Linux)  # Linux
  OS := linux
  CC = clang
  CXX = clang++
  AR = ar
  LIBS = -lc
  # 可选的 Linux 特定编译选项
  CFLAGS += -DLINUX
  LDFLAGS += -lrt
endif

ifeq ($(findstring CYGWIN,$(UNAME_S)), CYGWIN)  # Cygwin (Windows)
  OS := windows
  CC = clang
  CXX = clang++
  AR = ar
  LIBS = -lstdc++ -lm
  # 可选的 Cygwin 特定编译选项
  CFLAGS += -D_WINDOWS
endif

ifeq ($(findstring MINGW,$(UNAME_S)), MINGW)  # MinGW (Windows)
  OS := windows
  CC = clang
  CXX = clang++
  AR = ar
  LIBS = -lstdc++ -lm
  # 可选的 MinGW 特定编译选项
  CFLAGS += -D_WINDOWS
  LIB = "D:\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.39.33519\bin\Hostx64\x64\lib.exe"
endif

# 输出当前操作系统
$(info Detected OS: $(OS))

# 编译规则
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

%.o: %.cc
	$(CXX) $(CFLAGS) -c $< -o $@

# 创建静态库
libsysy.a: sylib.o sytimer.o
	$(AR) rcs $@ $^

# Windows 特定的静态库创建规则
ifeq ($(OS), windows)
  sysy.lib: sytimer.o sylib.o
	# 使用 lib 工具来生成 Windows 的静态库
	$(LIB) /OUT:sysy.lib *.o
endif

# 清理目标
clean:
	$(RM) $(wildcard *.o) $(wildcard *.a) $(wildcard *.lib)

# 构建目标
build: 
ifeq ($(OS), windows)
	@echo "Building Windows-specific sysy.lib"
	make sysy.lib
else
	@echo "Building Unix-specific libsysy.a"
	make libsysy.a
endif
	@echo "Building the library completed."

.PHONY: build clean
