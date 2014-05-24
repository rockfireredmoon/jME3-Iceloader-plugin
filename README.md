# Iceloader

A JME3 plugin library that adds additional functionality to the
asset loader system. The intention is to make remote asset loading efficient, and
the assets as hard to get at as possible.

Features include :-

* Asset encryption. Everywhere assets are stored, they may be encrypted. Iceloader then
  decrypts them on the fly as it loads them. An interface is provided to create the
  encryption key. This is the hard bit of protecting your assets. It is left to you
  to decide on the best way of hiding the key needed for decryption (see the 
  EncryptionContext class for how to do this). The default implementation uses a 
  not very safe system property to set the password.
* Asset caching. Anything downloaded from remote locations (via standard HTTP servers) 
  can be cached locally. 
* Special single-file cache that uses a Java implementation of FAT32.
* Up-to-date checks. Anything cached locally will be checked for freshness. Checks can 
  happen once per runtime. Or when you choose.
* Asset indexing. Local resources are indexed using "reflections" library. Server 
  supplied resources can have an index.dat at the root (tools supplied to create this). 
  Indexing also carries last modified times, so up-to-date checks are greatly speeded up 
  (they don't have to use individual if-modified-since request).
* Support for resources from Commons VFS. Possibly ultimately useless, but kind of cool, 
  this adds the possiblity of loading resources from FTP, SMB, SCP, SFTP, Tar files, 
  Zip files, ram disks and a whole lot more, just by adding the appropriate libraries.

_For encrypting and indexing assets as part of your build process, you will need the
Iceloader-Ant library as well._

## Dependencies

This plugin uses a few external libraries. The actual number needed at runtime will
depend on the usage.

* Commons VFS. http://commons.apache.org/proper/commons-vfs/. Required really, I may 
  remove this dependency at some point. Apache 2.0 license.
* Reflections.  http://code.google.com/p/reflections/. Used for indexing classpath
  resources.  "Other" open source license. Can't find actually what though.
* Fat32Lib. https://github.com/waldheinz/fat32-lib. Used for a single-file local cache.
  LGPL.
* Ant. For the encrypting and indexing of assets.

## Usage

After installation of the plugin, a new *Library* named **Iceloader** 
will be available to add to your project. 

1. Right click your project in the Projects view, select *Properties*.
1. Click on *Libraries* in the Categories list.
1. Select the *Compile* tab if it is not already selected.
1. Click *Add Library*.
1. Locate **Iceloader**, select it, then click *Add Library*.
1. Clikc *Ok* to save and dismiss the project properties window.

That's it, your project now includes the Iceloader library.

The next thing you will need to do is to configure the asset manager with your list
of custom locators (and loaders).

### Assets.cfg

As with the standard JME3 asset manager, the same simple text file is used to configure
the list of locators and loaders.

Place the following in your META-INF, and name it Assets.cfg. 

<pre>
LOCATOR / icemoon.iceloader.locators.ClasspathLocator
LOCATOR / icemoon.iceloader.locators.EncryptedAssetCacheLocator
LOCATOR / icemoon.iceloader.locators.EncryptedServerLocator

LOADER com.jme3.texture.plugins.AWTLoader : jpg, bmp, gif, jpeg, png
LOADER com.jme3.audio.plugins.WAVLoader : wav
LOADER com.jme3.audio.plugins.OGGLoader : ogg
LOADER com.jme3.cursors.plugins.CursorLoader : ani, cur, ico
LOADER com.jme3.material.plugins.J3MLoader : j3m
LOADER com.jme3.material.plugins.J3MLoader : j3md
LOADER com.jme3.material.plugins.ShaderNodeDefinitionLoader : j3sn
LOADER com.jme3.font.plugins.BitmapFontLoader : fnt
LOADER com.jme3.texture.plugins.DDSLoader : dds
LOADER com.jme3.texture.plugins.PFMLoader : pfm
LOADER com.jme3.texture.plugins.HDRLoader : hdr
LOADER com.jme3.texture.plugins.TGALoader : tga
LOADER com.jme3.export.binary.BinaryImporter : j3o
LOADER com.jme3.export.binary.BinaryImporter : j3f
LOADER com.jme3.scene.plugins.OBJLoader : obj
LOADER com.jme3.scene.plugins.MTLLoader : mtl
LOADER com.jme3.scene.plugins.ogre.MeshLoader : meshxml, mesh.xml
LOADER com.jme3.scene.plugins.ogre.SkeletonLoader : skeletonxml, skeleton.xml
LOADER com.jme3.scene.plugins.ogre.MaterialLoader : material
LOADER com.jme3.scene.plugins.ogre.SceneLoader : scene
LOADER com.jme3.scene.plugins.blender.BlenderModelLoader : blend
LOADER com.jme3.shader.plugins.GLSLLoader : vert, frag, glsl, glsllib
LOADER com.jme3.scene.plugins.ogre.MeshLoader : meshxml, mesh.xml
</pre>

This configuration will locate assets in the following manner :-

* Firstly the classpath will be check for the asset. If found, the asset will not be 
  decrypted or cached, it will just be returned to JME for processing as usual.

* If not found on the classpath, it will looked for in the local asset cache. If the asset
  was encrypted when it was cached, it will be decrypted on the fly. If the server locator
  MIGHT be used, the asset won't be provided immediately, instead it will wait until the
  server locator has checked it for freshness (once per runtime).

* If not found in the local asset cache, an attempt will be made to download an
  encrypted copy from an HTTP server. If found, a check will be made for freshness
  (using last modified times). If the server copy is newer than the local one (or there is 
  no loca copy), it will be downloaded, cached (encrypted), and then finally provided to 
  JME (decrypted), all on the fly. If our local copy is the same, it will just be returned
  to JME decrypted.

### Your Application

In order to take advantage of the indexing (i.e. if you require searching of assets at runtime
for some reason), or if you want to just take advantage of the faster freshness check that
uses the index file, you should use the supplied custom version of the asset manager in your
application.

This is also a way to configure your application to use a different Assets.cfg.

<pre>

    @Override
    public void initialize() {
        // For our example, we are using a Server Locator, so you should set the location of the HTTP server
        System.setProperty("iceloader.serverLocation", "http://myserver.com/path/to/assets/");

        assetManager = new ServerAssetManager(Thread.currentThread().getContextClassLoader().getResource("META-INF/Assets.cfg"));
        
        // This will create the asset indexes for all locators that support indexing.
        // Also allows fast freshness check for remote assets
        ((ServerAssetManager) assetManager).index();
        
        // Carry on as normal
        super.initialize();
    }

</pre>

### Encryption

If you are going to be using encrypted assets, you will need a way of providing the
decryption key to your application.

This is done through the EncryptionContext interface. The default implementation is 
unsafe, and rubbish, useful for testing only. Use the default at your own risk.

#### Default EncryptionContext

This uses the AES/CFB8/NoPadding cipher, and a simple password and salt for the
encryption key.

The default password is "password123?" and the default salt is "12345678". To change these
use the system properties _iceloader.password_ and _iceloader.salt_. 

#### A Custom EncryptionContext

You will need to create an implementation of EncryptionContext :-

<pre>
 class MyEncryptionContext extends EncryptionContext() {
    @Override
    public SecretKeySpec createKey() throws Exception {
        // Somehow create a SecretKeySpec
        return createMyKeySpec();
    }

    @Override
    public String getMagic() {
        // This is the special byte sequence used to identify encrypted assets
        return "!Magic!";
    }

    @Override
    public String getCipher() {
        // Chose your cipher
        return "AES/CFB8/NoPadding";
    }
};

// Now set it ..
EncryptionContext.set(new MyEncryptionContext());

</pre>

### Encrypting Your Assets and Creating Indexes

See the README.md of the Iceloader-Ant plugin here https://github.com/rockfireredmoon/jME3-Iceloader-Ant-plugin

### Single File Local Cache

A single dynamically growing local file may be used as a local asset cache. This is yet
another barrier to easy access to assets, although not a very good one, as it is actually
a virtual FAT32 file system. So given the right tools a user could access the files 
inside. However, the files inside are encrypted, and I have a use for this so it is 
included :)

To activate this, set the system property _iceloader.assetCache_ to fat32:///path/to/some/file.

### The Locators

Many of the locators can (and sometimes should) be configured. This is currently done
using some system properties. I may look for a better way at some point.

Depending on the locators you use, check the following. The defaults are unlikely to be
fine for your case.

#### icemoon.iceloader.locators.ClasspathLocator 

Much the same as the standard classpath locator, but with indexing support (provided by
"reflections" library).

#### icemoon.iceloader.locators.ClasspathCachingLocator 

Much the same as the ClasspathLocator, but will also allow later remote asset locators
check for the asset too. If there is one on a server, it would be used in preference.

#### icemoon.iceloader.locators.EncryptedClasspathLocator 

Much the same as the ClasspathLocator, but will assume the classpath assets are encrypted,
and decrypt them as they are returned to JME.

#### icemoon.iceloader.locators.AssetCacheLocator 

This will find in your local cache, that is is populated by other locators that may 
download assets. If an asset is found here, it will be returned to JME (eventually, 
after some an optional freshness check). 

#### icemoon.iceloader.locators.EncryptedAssetCacheLocator 

This locator extends AssetCacheLocation and  will find encrypted stuff in your local cache, 
that is is populated by other locators that may download assets. If an asset is found here, 
it will be returned to JME (eventually, after some an optional freshness check), decrypted. 

##### Properties 

* _iceloader.assetCache_. Root of where assets are actually locally cached. This may
  be a Commons VFS URI. For example, to store in /tmp/myassetseither file:///tmp/myassets 
  or /tmp/myassets would work.

#### icemoon.iceloader.locators.FileLocator 

Finds 'local' resources, but allows use of Commons VFS URI instead. So local resources
could actually be remote. Will also cache resources retrieved this way if a cache
locator is in use.

##### Properties 

* _iceloader.fileLocation_. Root of where assets are actually locally loaded from. This
is a Commons VFS URI, so, file:///home/user/Documents/Assets, or 
ftp://anonymous@someserver.org/path/to/assets would be valid.

#### icemoon.iceloader.locators.ServerLocator 

This locator will download unencrypted assets from a remote HTTP server. It also has all 
the support needed for interacting with Iceloader's caching locators, and so is also  used
to do fresness checks. 

##### Properties 

* _iceloader.serverLocation_. Root of where assets are actually locally loaded from. 
The default is http://localhost/. Make sure you end the URL with '/'.

#### icemoon.iceloader.locators.EncryptedServerLocator 

This extension of ServerLocator expects the assets to be encrypted. It will decrypt
them on-the-fly (also caching when appropriate).

##### Properties 

* _iceloader.serverLocation_. Root of where assets are actually locally loaded from. 
The default is http://localhost/. Make sure you end the URL with '/'.