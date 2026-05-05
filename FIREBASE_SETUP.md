# Firebase Setup for 24Property Android

## Step 1: Add Android App to Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select the existing project: **cityproperty-78681**
3. Click the settings gear icon -> **Project settings**
4. Under **Your apps**, click **Add app** -> **Android**
5. Enter package name: `com.example.myapplication`
6. Enter app nickname: `24Property Android`
7. Click **Register app**

## Step 2: Generate SHA-1 Certificate Fingerprint

Run this command in your terminal:

```bash
# For debug key
cd ~/.android
keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android

# For release key (if you have one)
keytool -list -v -keystore YOUR_RELEASE_KEYSTORE_PATH -alias YOUR_ALIAS
```

Copy the **SHA-1** fingerprint and paste it into the Firebase Console when prompted.

## Step 3: Download google-services.json

After registering the app and adding the SHA-1:
1. Click **Download google-services.json**
2. Replace the existing placeholder file at:
   `24PropertyAndroidNew/app/google-services.json`

## Step 4: Update Web Client ID

1. In Firebase Console, go to **Project Settings** -> **General**
2. Under **Your apps**, find the **Web API Key** or check **Service accounts**
3. Alternatively, go to Google Cloud Console -> APIs & Services -> Credentials
4. Find the **Web client (auto created by Google Service)** OAuth 2.0 client ID
5. Copy the **Client ID** and update it in:
   `app/src/main/res/values/strings.xml`
   ```xml
   <string name="default_web_client_id">YOUR_ACTUAL_WEB_CLIENT_ID</string>
   ```

## Step 5: Enable Google Sign-In

1. In Firebase Console, go to **Authentication** -> **Sign-in method**
2. Enable **Google** provider
3. Save

## Step 6: Build & Run

```bash
./gradlew assembleDebug
```

Or run directly from Android Studio.
