$app = Get-WmiObject -Query "SELECT * FROM Win32_Product WHERE Name = '@{application.name}'"

if ($app -eq $null) {
	echo '@{application.name} is not installed.'
} else {
	$app.Uninstall()
	echo '@{application.name} has been uninstalled.'
}
