### Handmade Slideshow

for Android

#### Installation

```
git submodule add https://github.com/diewland/handmade-slideshow.git app/src/main/java/com/diewland/hmslideshow
git submodule update --init --recursive
```

Add EXO Player library to `app/build.gradle`

```
implementation 'com.google.android.exoplayer:exoplayer:2.14.0'
```

#### Update submodule

```
git submodule update --remote --recursive
```

#### Demo Project

https://github.com/diewland/handmade-slideshow-demo

---

#### Remove submodule

```
# Remove the submodule entry from .git/config
git submodule deinit -f path/to/submodule

# Remove the submodule directory from the superproject's .git/modules directory
rm -rf .git/modules/path/to/submodule

# Remove the entry in .gitmodules and remove the submodule directory located at path/to/submodule
git rm -f path/to/submodule
```
https://stackoverflow.com/a/36593218/466693
