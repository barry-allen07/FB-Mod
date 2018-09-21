def destfile = { r -> 
	ant.project.properties.'dir.dist' + '/lib/' + r.file.name.replaceAll(/.lib|.jdk15on|.java16/, '').toLowerCase()
}

project.references.'jar.classpath'.each { r ->
	ant.jar(destfile: destfile(r), index: 'yes', indexMetaInf: 'yes', compress: 'no') {
		zipfileset(src: r) {
			exclude(name: 'META-INF/MANIFEST.MF')
			exclude(name: 'META-INF/INDEX.LIST')
			exclude(name: 'META-INF/*.RSA')
			exclude(name: 'META-INF/*.DSA')
			exclude(name: 'META-INF/*.SF')
			exclude(name: 'META-INF/*.EC')
		}
	}
}
