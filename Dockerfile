FROM tomcat:10.1-jdk11-corretto

# タイムゾーンを日本に設定
ENV TZ=Asia/Tokyo

# 既存のROOTアプリケーションを削除し、正しい配置用のフォルダを作成
RUN rm -rf /usr/local/tomcat/webapps/ROOT && \
    mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes

# 作業スペースを作成
WORKDIR /app

# リポジトリ内のすべてのファイルを一旦コピー
COPY . .

# JSPファイル（プレイヤー画面・司会者画面）をROOT直下に確実に配置
RUN find . -name "index.jsp" -exec cp {} /usr/local/tomcat/webapps/ROOT/ \; && \
    find . -name "admin.jsp" -exec cp {} /usr/local/tomcat/webapps/ROOT/ \;

# JavaファイルをTomcatの共通ライブラリを使ってコンパイルし、classes直下に配置
RUN find . -name "*.java" | xargs javac -classpath "/usr/local/tomcat/lib/*" -d /usr/local/tomcat/webapps/ROOT/WEB-INF/classes

# 【カード表示のための重要設定】セッション（記憶部屋）のクッキーパスをEclipse互換に強制変更
RUN sed -i 's/<Context>/<Context sessionCookiePathUsesTrailingSlash="false">/' /usr/local/tomcat/conf/context.xml

# ポート番号の設定（Render用）
EXPOSE 8080
CMD ["catalina.sh", "run"]
