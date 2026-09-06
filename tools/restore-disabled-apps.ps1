$ErrorActionPreference = 'Stop'

$adb = Join-Path $env:USERPROFILE 'Tools\Android\platform-tools\adb.exe'
$packages = @(
    'com.google.android.apps.cloudprint',
    'com.google.android.apps.currents',
    'com.google.android.apps.docs',
    'com.google.android.apps.inputmethod.hindi',
    'com.google.android.apps.magazines',
    'com.google.android.apps.plus',
    'com.google.android.calendar',
    'com.google.android.ears',
    'com.google.android.gm',
    'com.google.android.inputmethod.korean',
    'com.google.android.inputmethod.pinyin',
    'com.google.android.keep',
    'com.google.android.marvin.talkback',
    'com.google.android.talk',
    'com.google.android.videos',
    'com.google.android.widget.userinfo',
    'com.google.android.youtube',
    'com.google.earth',
    'com.hp.android.printservice',
    'com.nuance.xt9.input',
    'com.quickoffice.android',
    'jp.co.omronsoft.iwnnime.ml'
)

foreach ($package in $packages) {
    & $adb shell pm enable $package
}
