package io.github.sifisofakude.util.archive

import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

import java.util.jar.Manifest
import java.util.jar.Attributes

import java.util.zip.ZipException

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

import io.github.sifisofakude.filesystem.*

/**
* Listener used by [ArchiveUtil] to receive informational,
* warning, and error events during archive operations.
*/
interface ArchiveListener	{
 	/**
	  * Called when an informational event occurs.
	  * 
	  * @param info descriptive information message.
	  */	
	fun onInfo(info: String)

	/**
	  * Called when a non-fatal warning occurs.
	  * 
	  * @param warning warning message.
    */
	fun onWarning(warning: String)

	
  /**
	  * Called when an error occurs.
	  * 
	  * @param error error description.
	  * @param cause optional exception associated with the error.
    */
	fun onError(error: String, cause: Throwable? = null)
}

/**
 * Default implementation of [ArchiveListener].
 *
 * Messages are written to the standard output stream.
 * Errors additionally print the associated stack trace
 * when a cause is provided.
 */
class DefaultArchiveListener() : ArchiveListener	{
	override fun onInfo(info: String) { println("[INFO] $info") }
	override fun onWarning(warning: String) { println("[WARNING] $warning") }
	
	override fun onError(error: String, cause: Throwable?) { 
		println("[ERROR] $error")
		cause?.printStackTrace()
	}
}


/**

	* Utility class for creating, updating, and extracting
	* JAR, ZIP, and AAR archives.
	* 
	* @property fs filesystem implementation used for file operations.
	* @property listener optional listener used to receive progress
	* and error notifications.
  */
open class ArchiveUtil(
	private val fs: FileSystemUtil,
	private val listener: ArchiveListener? = null
)	{
	private val supported = listOf("jar","zip","aar","war","ear")

	/**
	
		* Updates an existing archive.
		* 
		* Existing entries with matching paths are replaced by entries
		* from [files]. Entries not present in [files] are preserved.
		* 
		* @param jarFile archive to update.
		* @param files replacement and additional files.
		* @param mainClass optional main class written to the manifest.
		* 
		* @return true if the update completed successfully,
		* false otherwise.
	  */
	fun updateArchive(
		jarFile: String,
		files: List<String>,
		mainClass: String? = null
	): Boolean	{
		if(!fs.exists(jarFile)) return false
		if(!fs.isFile(jarFile)) return false

		val extension = File(jarFile).extension
		if(extension !in supported)	{
			return false
		}

		listener?.onInfo("Updating $jarFile...")

		val inputStream = fs.openInputStream(jarFile) ?: return false
		val jis = JarInputStream(inputStream)
		val manifest = jis.getManifest() ?: Manifest()
		val resolvedFiles = fs.resolveFiles(files,emptySet())

		handleManifest(manifest,mainClass)
		
		return writeArchive(jarFile,jis,resolvedFiles,manifest)
	}

	/**
	
		* Creates a new archive.
		* 
		* Existing files are overwritten when [overwrite] is true.
		* The generated archive will contain all entries provided in
		* [files]. If specified, [mainClass] is written to the archive
		* manifest.
		* 
		* @param output output archive path.
		* @param files files to include in the archive.
		* @param mainClass optional main class written to the manifest.
		* @param overwrite whether an existing archive should be replaced.
		* 
		* @return true if the archive was created successfully,
		* false otherwise.
	  */
	fun createArchive(
		output: String, 
		files: List<String>, 
		mainClass: String? = null,
		overwrite: Boolean = true,
		autoManifest: Boolean = false
	): Boolean	{
		val extension = File(output).extension
		if(extension !in supported)	{
			return false
		}

		if(fs.exists(output))	{
			if(overwrite) fs.delete(output)
			else return false
		}
		
		fs.createFile(output)

		val manifest = if((mainClass == null && files.isNotEmpty() && !autoManifest) || extension != "jar")	{
			null
		}else	{
			Manifest()
		}

		handleManifest(manifest,mainClass)

		listener?.onInfo("Creating $output")
		val resolvedFiles = fs.resolveFiles(files,emptySet())
		
		return writeArchive(output,null,resolvedFiles,manifest)
	}


 /**
 
	 * Extracts one or more archives to a destination directory.
	 * 
	 * Each archive is extracted into a directory named after
	 * the archive file without its extension.
	 * 
	 * @param outputDir destination directory.
	 * @param inputFiles archives to extract.
	 * 
	 * @return true if extraction completed successfully,
	 * false otherwise.
   */
	fun extractArchive(
		outputDir: String,
		inputFiles: List<String>
	): Boolean	{
		var success = false
		
		try	{
			val resolvedPath = fs.createDirectory(outputDir)
			val resolvedFiles = fs.resolveFiles(inputFiles,emptySet())
			
			if(resolvedPath != null)	{
				resolvedFiles.forEach	{ fileSource ->
					listener?.onInfo("Extracting ${fileSource.relativePath} to $outputDir")
					
					val extension = File(fileSource.relativePath).extension
					if(extension in supported)	{
						val fileInputStream = fs
							.openInputStream(fileSource.absolutePath) 
							?: return@forEach
							
						JarInputStream(fileInputStream).use	{ jis ->
							var entry = jis.getNextJarEntry()

							val jarOutputDir = File(fileSource.relativePath)
								.nameWithoutExtension

							
							val buffer = ByteArray(64 * 1024)

							while(entry != null)	{
								listener?.onInfo("Processing entry ${entry.name}")
								
								if(!entry.isDirectory())	{
									val entryOutput = fs.openOutputStream(
										"$resolvedPath${File.separator}$jarOutputDir${File.separator}${entry.getName()}"
									)

									entryOutput?.use	{ stream ->
										var bytesRead = jis.read(buffer)
										while(bytesRead != -1)	{
											stream.write(buffer,0,bytesRead)
											bytesRead = jis.read(buffer)
										}
										stream.flush()
									}
								}
								jis.closeEntry()
								entry = jis.getNextJarEntry()
								
								success = true
							}
						}
					}
				}
				listener?.onInfo("Extraction completed successfully")
			}
		}catch(e: Exception)	{
			listener?.onError(e?.message ?: "Unknown error has occurred",e)
		}
		return success
	}

	private fun handleManifest(manifest: Manifest?,mainClass: String?)	{
		if(manifest != null)	{
			val attributes = manifest.mainAttributes

			if(attributes.getValue(Attributes.Name.MANIFEST_VERSION) == null)	{
				attributes.put(Attributes.Name.MANIFEST_VERSION,"1.0")
			}
			
			if(mainClass != null)	{
				attributes.put(Attributes.Name.MAIN_CLASS,mainClass)
			}
		}
	}

	private fun writeArchive(
		output: String,
		jis: JarInputStream? = null,
		files: List<FileSource>,
		manifest: Manifest? = null
	): Boolean	{
		var completed = false
		val parentFile = fs.getParentFile(output) ?: ""
		var tmpOutFile = ".tmp_${System.currentTimeMillis()}.jar"

		if(parentFile.isNotEmpty()) tmpOutFile = "$parentFile${File.separator}$tmpOutFile"
		fs.createFile(tmpOutFile)

		val updating = jis != null

		val replacements = files.associateBy	{
			it.relativePath.replace("\\","/")
		}

		try	{
			fs.openOutputStream(tmpOutFile)?.use	{ os ->
				val jarOutputStream = if(manifest == null)	{
					JarOutputStream(os)
				}else	{
					JarOutputStream(os,manifest)
				}
				
				jarOutputStream.use	{ jos ->

					jis?.use	{
					
						var entry = jis.getNextJarEntry()
						val buffer = ByteArray(64 * 1024)
						
						while(entry != null)	{
							if(entry.name in replacements)	{
								jis.closeEntry()
								entry = jis.getNextJarEntry()
								continue
							}

							if(entry.name.equals("META-INF/MANIFEST.MF",ignoreCase = true))	{
								jis.closeEntry()
								entry = jis.getNextJarEntry()
								continue
							}
							
							listener?.onInfo("Processing entry ${entry.name}")

							try	{
								jos.putNextEntry(entry)

								var bytesRead = jis.read(buffer)
								
								while(bytesRead != -1)	{
									jos.write(buffer,0,bytesRead)
									bytesRead = jis.read(buffer)
								}
								jis.closeEntry()
								jos.closeEntry()
							}catch(e: ZipException) {
								listener?.onWarning("${e.message}")
							}catch(e: IOException)	{
								listener?.onError("Couldn't write entry ${entry.name}",e)
								throw e
							}
							
							entry = jis.getNextJarEntry()
						}
					}
					
					replacements.values.forEach	{ file ->

						val entry = JarEntry("${file.relativePath.replace("\\","/")}")
						
						try	{
							listener?.onInfo("Processing entry ${entry.name}")

							jos.putNextEntry(entry)

							fs.openInputStream(file.absolutePath)?.use	{ stream ->
								
								
								val buffer = ByteArray(64 * 1024)
								var bytesRead = stream.read(buffer)
								
								while(bytesRead != -1)	{
								
									jos.write(buffer,0,bytesRead)
									bytesRead = stream.read(buffer)
									
								}
							}
							jos.closeEntry()
						}catch(e: ZipException) {
							listener?.onWarning(e?.message ?: "ZipException has occurred")
						}catch(e: IOException)	{
							listener?.onError("Couldn't write entry ${entry.name}",e)
							throw e
						}
					}
					jos.flush()
				}
				os.flush()
				completed = true
			}
		}catch(e: Exception)	{
			e.printStackTrace()
			listener?.onError(e?.message ?: "Unknown error has occurred",e)
		}finally	{
			if(completed)	{
				fs.delete(output)
				fs.rename(tmpOutFile,fs.getName(output))

				listener?.onInfo(
					if(updating) "Updated $output successfully"
					else "Created $output successfully"
				)
			}else	{
				fs.delete(tmpOutFile)
			}
		}
		return completed
	}
}

fun main(args: Array<String>)	{
	val fs = JvmFileSystem()

	var action: String? = null
	var outputFile: String? = null
	var inputFile: String? = null
	var mainClass: String? = null
	val inputFiles = mutableListOf<String>()

	val archiveUtil = ArchiveUtil(fs,DefaultArchiveListener())

	var i = 0
	while(i < args.size)	{
		when(args[i])	{
			"-c" ->	{
				action = "create"
				if(i+1 < args.size && !args[i+1].startsWith("-"))	{
					i++
					outputFile = args[i]
				}
			}
			
			"-u" ->	{
				action = "update"
			}
			
			"-e" ->	{
				action = "extract"
			}
			
			"-m" ->	{
				if(i+1 < args.size && !args[i+1].startsWith("-"))	{
					i++
					mainClass = args[i]
				}
			}
			
			"-o" ->	{
				if(i+1 < args.size && !args[i+1].startsWith("-"))	{
					i++
					outputFile = args[i]
				}
			}

			else ->	{
				inputFiles.add(args[i])
			}
		}
		i ++
	}

	when(action)	{
		"create" ->	{
			outputFile?.let	{
				archiveUtil.createArchive(
					it,
					inputFiles,
					mainClass
				)
			}
		}

		"update" ->	{
			inputFile?.let	{
				archiveUtil.updateArchive(
					it,
					inputFiles,
					mainClass
				)
			}
		}
		
		"extract" ->	{
			inputFile?.let	{
				val destination = outputFile ?: fs.resolvePath(".")
				
				archiveUtil.extractArchive(
					destination,
					inputFiles
				)
			}
		}
	}
}
