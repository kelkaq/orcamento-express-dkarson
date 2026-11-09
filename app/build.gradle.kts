import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.dkarson.orcamentoexpress"
    minSdk = 24
    targetSdk = 36
    versionCode = 5
    versionName = "1.4"

    // Endpoint público HTTPS do verificador SEGURO, nunca credenciais Google no APK.
    val verifyEndpoint = System.getenv("BILLING_VERIFY_URL")
      ?.trim()?.takeIf { it.isNotEmpty() }
      ?: "https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api/v1/play/verify"
    buildConfigField("String", "BILLING_VERIFY_URL",
      "\"" + verifyEndpoint.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

    val cloudEndpoint = System.getenv("CLOUD_API_URL")
      ?.trim()?.takeIf { it.isNotEmpty() }
      ?: "https://hajxpqnmeabyvfctlygp.supabase.co/functions/v1/orcamento-api"
    buildConfigField("String", "CLOUD_API_URL",
      "\"" + cloudEndpoint.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

    // ID publico do cliente OAuth Web (não é segredo); só configurado pelo proprietário.
    val googleWebClientId = System.getenv("GOOGLE_WEB_CLIENT_ID").orEmpty().trim()
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",
      "\"" + googleWebClientId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // A chave Release pertence exclusivamente ao proprietário do app.
  // Nunca distribua o .jks nem suas senhas em ZIPs de código ou repositórios.
  signingConfigs {
    // Chave EXCLUSIVAMENTE para o pacote de testes.
    // Nunca use essa chave para publicar na Play Store ou assinar a versão release.
    create("dkarsonDebug") {
      storeFile = file("${rootDir}/app/dkarson-test-only.p12")
      storeType = "PKCS12"
      storePassword = "android"
      keyAlias = "dkarson-debug"
      keyPassword = "android"
    }
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Nome e assinatura ESTÁVEIS para futuras atualizações do ambiente de testes.
      // Identificador diferente dos pacotes antigos: permite recuperar dados via backup.
      applicationIdSuffix = ".teste.unificado"
      versionNameSuffix = "-teste"
      signingConfig = signingConfigs.getByName("dkarsonDebug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  // Google Play Billing Library 9 (obrigatória para assinaturas na Google Play).
  implementation("com.android.billingclient:billing:9.1.0")
  implementation("androidx.credentials:credentials:1.6.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  implementation("androidx.work:work-runtime-ktx:2.12.0")
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
