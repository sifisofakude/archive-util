# ArchiveUtil

A lightweight Kotlin utility for creating, updating, and extracting JAR, 
ZIP, and AAR archives. Built on top of `FileSystemUtil`, allowing it to work 
with standard JVM filesystems, Android SAF storage, and custom filesystem 
implementations.

---

## Why ArchiveUtil?

Unlike most archive libraries that operate directly on `java.io.File`,
ArchiveUtil is built on top of `FileSystemUtil`, allowing the same code to
work on:

- JVM desktop applications
- Android SAF storage
- Virtual filesystems
- Custom filesystem implementations

---

## Features

- Create new archives (`.jar`, `.zip`, `.aar`,`war`,`ear`)
- Update existing archives
- Replace existing entries during updates
- Extract archives to a directory
- Manifest support
- Optional `Main-Class` support
- Progress, warning, and error callbacks through listeners
- Safe archive updates using temporary files to avoid 
corrupting existing archives
- Works with any `FileSystemUtil` implementation
- Replaces duplicate archive entries automatically during updates

---

## Installation

Add `ArchiveUtil` to your project and ensure that the filesystem module is 
available.

`implementation("io.github.sifisofakude.archiveutil:archiveutil:1.0.0")`

---

## Creating an Archive

```kotlin
val fs = JvmFileSystem()

val archiveUtil = ArchiveUtil(
    fs,
    DefaultListener()
)

archiveUtil.createArchive(
    output = "app.jar",
    files = listOf("build/classes"),
    mainClass = "com.example.Main"
)
```

---

## Updating an Archive

```kotlin
val fs = JvmFileSystem()

val archiveUtil = ArchiveUtil(
    fs,
    DefaultListener()
)

archiveUtil.updateArchive(
    jarFile = "app.jar",
    files = listOf("new-classes")
)
```

**During updates:**

- Existing entries with matching paths are replaced.
- New entries are added.
- Entries not supplied remain unchanged.

---

## Extracting an Archive

```kotlin
val fs = JvmFileSystem()

val archiveUtil = ArchiveUtil(
    fs,
    DefaultListener()
)

archiveUtil.extractArchive(
    outputDir = "output",
    inputFiles = listOf("app.jar"),
)
```

Each archive is extracted into a directory named after 
the archive file, excluding its extension.

**Example:**

`app.jar`

becomes:

```
output/
└── app/
```
---

## Extraction Notes

Parent directories are created automatically through the underlying
`FileSystemUtil` implementation.

No manual directory creation is required before extracting archives.

---

## Manifest Support

`ArchiveUtil` automatically creates a manifest when needed.

**Example:**

```kotlin
archiveUtil.createArchive(
    output = "app.jar",
    files = listOf("classes"),
    mainClass = "com.example.Main"
)
```

**Result:**

```text
Manifest-Version: 1.0
Main-Class: com.example.Main
```

Existing manifests are preserved when updating archives.

---

## Listener Support

Implement `JarListener` to receive progress updates.

```kotlin
class MyListener : ArchiveUtilListener {

    override fun onInfo(info: String) {
        println(info)
    }

    override fun onWarning(warning: String) {
        println(warning)
    }

    override fun onError(
        error: String,
        cause: Throwable?
    ) {
        println(error)
    }
}
```

**Usage:**

```kotlin
val archiveUtil = ArchiveUtil(
    fs,
    MyListener()
)
```

---

## Command Line Usage

**Create Archive**

`archiveutil -c app.jar classes resources`

**Create Executable JAR**

`archiveutil -c app.jar classes -m com.example.Main`

**Update Archive**

`archiveutil -u app.jar classes`

**Extract Archive**

`archiveutil -e app.jar -o output`

---

### Supported Formats

- JAR (`.jar`)
- ZIP (`.zip`)
- WAR (`.war`)
- EAR (`.ear`)
- Android Archive (`.aar`)

---

### Return Values

All public operations return a `Boolean`:

```kotlin
val success = archiveUtil.createArchive(...)
```

`true` indicates the operation completed successfully.

`false` indicates the operation failed or the provided archive type is unsupported.

---

## Filesystem Support

`ArchiveUtil` operates through the `FileSystemUtil` abstraction and is not 
tied to a specific storage implementation.

**Supported implementations include:**

- `JvmFileSystem`
- `AndroidSafFileSystem`
- Any custom implementation of `FileSystemUtil`

**Example:**

```kotlin
val fs: FileSystemUtil = JvmFileSystem()

val archiveUtil = ArchiveUtil(
    fs,
    DefaultListener()
)
```

**On Android:**

```kotlin
val fs: FileSystemUtil = AndroidSafFileSystem(context)

val archiveUtil = ArchiveUtil(
    fs,
    DefaultListener()
)
```

**This allows ArchiveUtil to work with:**

- Local files
- Android Storage Access Framework (SAF)
- Document providers
- Custom virtual filesystems
- Cloud-backed filesystem implementations

---

## License

Licensed under the MIT License.
