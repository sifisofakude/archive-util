plugins	{
	id("org.jetbrains.kotlin.android")
	id("com.android.library")
}

android	{
  namespace = "io.github.sifisofakude.util.archiveutil"
	compileSdk = 36
	
	defaultConfig {
	  minSdk = 23
	}
}

dependencies  {
  implementation("io.github.sifisofakude:filesystem-android:0.3.1")
  implementation(project(":common"))
}

