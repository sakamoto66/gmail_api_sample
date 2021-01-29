import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleService.class);
    private static GoogleService instance = null;

    // Global instance of the JSON factory.
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // Global instance of the HTTP transport.
    private static HttpTransport HTTP_TRANSPORT = null;

    // Reader for client secret.
    private Reader clientSecretReader;

    // Collection of authorization scopes
    private Collection<String> authorizationScopes;

    // Directory to store user credentials
    private String credentialStoreDirectory;

    // application name
    private String applicationName;

    public static GoogleService factory(){
        if( instance == null ) {
            // 秘密鍵
            final String clientSecretFile = System.getProperty("google.api.clientsecret.file", "/google_client_secret.json");
            InputStream in = GoogleService.class.getResourceAsStream(clientSecretFile);
            Reader reader = new InputStreamReader(in);

            // 認証周りの設定ファイルの格納フォルダ
            final String credentialsFolder =  System.getProperty("google.api.credentials.dir", "credentials");

            // スコープ
            final String scope =  System.getProperty("google.api.scopes", GmailScopes.GMAIL_READONLY);
            List<String> scopesList = Arrays.asList(scope.split(","));

            // アプリケーション名
            final String applicationName = System.getProperty("google.api.application.name", "app");

            instance = new GoogleService(reader, scopesList, credentialsFolder, applicationName);
        }
        return instance;
    }

    /**
     * Constructor.
     *
     * @param clientSecretReader reader for client secret
     * @param authorizationScopes collection of authorization scopes
     * @param credentialStoreDirectory directory to store user credentials
     * @param applicationName application name
     */
    public GoogleService(Reader clientSecretReader, Collection<String> authorizationScopes, String credentialStoreDirectory, String applicationName) {
        this.clientSecretReader = clientSecretReader;
        this.authorizationScopes = authorizationScopes;
        this.credentialStoreDirectory = credentialStoreDirectory;
        this.applicationName = applicationName;
    }

    // Credential.
    private Credential credential = null;

    /**
     * Creates an authorized Credential object.
     *
     * @throws IOException
     * @throws GeneralSecurityException
     */
    protected void authorize() throws IOException, GeneralSecurityException {
        if (credential != null) {
            return;
        }
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);
        // instance of the {@link FileDataStoreFactory}.
        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File(credentialStoreDirectory));
        // ↑ Windowsでは「unable to change permissions～」ログが出力される
        if (HTTP_TRANSPORT == null) {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        }
        // Build flow and trigger user authorization request.
        Builder builder = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, authorizationScopes);
        builder.setDataStoreFactory(dataStoreFactory).setAccessType("offline");
        // ↑
        // AccessType「offline」でRefreshTokenを得る(AccessTokenのexpire前60秒以後のAPI呼出時に自動refreshが行われるようになる)
        builder.addRefreshListener(new CredentialRefreshListener() {
            @Override
            public void onTokenResponse(Credential credential, TokenResponse tokenResponse) throws IOException {
                LOGGER.info("AccessTokenのrefreshが成功しました。(AccessToken=[{}], ExpiresInSeconds={}, RefreshToken=[{}])", credential.getAccessToken(), credential.getExpiresInSeconds(), credential.getRefreshToken());
            }

            @Override
            public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse) throws IOException {
                LOGGER.error("AccessTokenのrefreshが失敗しました。(Error=[{}], ErrorDescription=[{}], ErrorUri=[{}])", tokenErrorResponse.getError(), tokenErrorResponse.getErrorDescription(), tokenErrorResponse.getErrorUri());
            }
        });
        // ↑ AccessTokenのrefresh後のListner
        GoogleAuthorizationCodeFlow flow = builder.build();
        credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        // ↑ 初回はブラウザがGoogleの許可リクエスト画面を表示する(関連ログも出力される)
        // → 「許可」押下でローカルJettyにリダイレクトされ、Credentialがファイルに保存される
        // → 以後はCredentialファイルがある限りブラウザは起動しない(自動refreshのおかげ)
        // → サーバで実行する場合はローカルPCで作成したCredentialファイルをサーバに配置しておく
        // → 何らかのエラーでサーバ上のCredentialファイルが無効になった場合は当時のファイルを再度配置する
        LOGGER.info("AccessTokenを取得しました。(AccessToken=[{}], ExpiresInSeconds={}, RefreshToken=[{}])", credential.getAccessToken(), credential.getExpiresInSeconds(), credential.getRefreshToken());
    }

    /**
     * Build and return an authorized Gmail client service.
     *
     * @return an authorized Gmail client service
     */
    public Gmail gmail() {
        try {
            if (credential == null) {
                authorize();
            }
            return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(applicationName).build();
        } catch(Exception e) {
            throw new RuntimeException("faild crate Gmail instance", e);
        }
    }
}