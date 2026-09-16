#!/bin/sh
# ---------------------------------------------------------------------------
# 最小化 Maven Wrapper(macOS / Linux)
# 首次运行会自动下载 Apache Maven 到 ~/.m2/wrapper/dists 并缓存,之后直接复用。
# 版本需与 .mvn/wrapper/maven-wrapper.properties 中的 distributionUrl 保持一致。
# ---------------------------------------------------------------------------
set -e

MAVEN_VERSION=3.9.9
MAVEN_DIST_DIR="$HOME/.m2/wrapper/dists"
MAVEN_HOME="$MAVEN_DIST_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_ARCHIVE="$MAVEN_DIST_DIR/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

# 定位项目根目录(脚本所在目录)
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# 下载并解压 Maven(若尚未缓存)
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo ">>> 首次运行,下载 Apache Maven $MAVEN_VERSION ..."
  mkdir -p "$MAVEN_DIST_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$MAVEN_URL" -o "$MAVEN_ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$MAVEN_URL" -O "$MAVEN_ARCHIVE"
  else
    echo "错误:需要 curl 或 wget 来下载 Maven" >&2
    exit 1
  fi
  ( cd "$MAVEN_DIST_DIR" && unzip -q -o "$MAVEN_ARCHIVE" )
fi

# 确保 JAVA_HOME 可用(macOS 用 /usr/libexec/java_home 定位 JDK)
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  export JAVA_HOME=$(/usr/libexec/java_home)
fi

cd "$PROJECT_DIR"
exec "$MAVEN_HOME/bin/mvn" "$@"
