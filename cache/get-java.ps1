# @{title} for @{jdk.name} @{jdk.version}


$ErrorActionPreference = "Stop"


# JDK version identifiers
$JDK_ARCH = "$ENV:PROCESSOR_ARCHITECTURE"

Switch ($JDK_ARCH) {
	AMD64 {
		$JDK_URL = "http://download.oracle.com/otn-pub/java/jdk/10.0.2+13/19aef61b38124481863b1413dce1855f/jre-10.0.2_windows-x64_bin.tar.gz"
		$JDK_SHA256 = "cc5362d17c8baaa84c6bc63bba79fe8c2ec3d2ff2a2ec239482ae0b7cf03887a"
	}
	x86 {
		$JDK_URL = "@{jre.windows.x86.url}"
		$JDK_SHA256 = "@{jre.windows.x86.sha256}"
	}
	default {
		throw "CPU architecture not supported: $JDK_ARCH"
	}
}


# fetch JDK
$JDK_TAR_GZ = Split-Path -Leaf $JDK_URL

if (!(test-path $JDK_TAR_GZ)) {
	Write-Output "Download $JDK_TAR_GZ"
	$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
	$cookie = New-Object System.Net.Cookie 
	$cookie.Name = "oraclelicense"
	$cookie.Value = "accept-securebackup-cookie"
	$cookie.Domain = "oracle.com"
	$session.Cookies.Add($cookie)
	Invoke-WebRequest -UseBasicParsing -WebSession $session -Uri $JDK_URL -OutFile $JDK_TAR_GZ
}


# verify archive via SHA-256 checksum
$JDK_SHA256_ACTUAL = (Get-FileHash -Algorithm SHA256 $JDK_TAR_GZ).hash.toLower()
Write-Output "Expected SHA256 checksum: $JDK_SHA256"
Write-Output "Actual SHA256 checksum: $JDK_SHA256_ACTUAL"

if ($JDK_SHA256 -ne $JDK_SHA256_ACTUAL) {
	throw "ERROR: SHA256 checksum mismatch"
}


# extract and link only if explicitly requested
if ($args[0] -ne "install") {
	Write-Output "Download complete: $JDK_TAR_GZ"
	return
}


# use 7-Zip to extract tar
Write-Output "Extract $JDK_TAR_GZ"
& 7z e -aos $JDK_TAR_GZ
& 7z x -aos ([System.IO.Path]::GetFileNameWithoutExtension($JDK_TAR_GZ))


# find java executable
$JAVA_EXE = Get-ChildItem -recurse -include java.exe | Sort-Object LastWriteTime | Select-Object -ExpandProperty FullName -Last 1

# test
Write-Output "Execute ""$JAVA_EXE"" -XshowSettings -version"
& $JAVA_EXE -XshowSettings -version


# set %JAVA_HOME% and add java to %PATH%
$JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JAVA_EXE)

Write-Output "`nPlease add JAVA_HOME\bin to the PATH if you have not done so already:"
Write-Output "`n`t%JAVA_HOME%\bin"
Write-Output "`nPlease set JAVA_HOME:"
Write-Output "`n`tsetx JAVA_HOME ""$JAVA_HOME"""
