plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("dev.kioba.anchor")
}

android {

  namespace = "dev.kioba.anchor.features.main.ui"

  @Suppress("UnstableApiUsage")
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.4.7"
  }
}

dependencies {
//  implementation("dev.kioba:anchor:0.0.1")
  implementation("androidx.core:core-ktx:1.10.1")
  implementation("androidx.compose.ui:ui:1.4.3")
  implementation("androidx.compose.material:material:1.4.3")
  implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
  implementation("androidx.activity:activity-compose:1.7.2")
  implementation("androidx.activity:activity-ktx:1.7.2")
  implementation(projects.anchor)
  implementation(projects.features.main.presentation)
  implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
  debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")

//  testImplementation 'junit:junit:4.13.2'
//  androidTestImplementation 'androidx.test.ext:junit:1.1.5'
//  androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
//  androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.4.3"
//  debugImplementation "androidx.compose.ui:ui-tooling:1.4.3"
}
