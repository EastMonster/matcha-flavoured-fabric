import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.util.zip.ZipFile

// Arguments are the source resources, vanilla client JAR, and generated resources directory.
def resources = new File(args[0])
def output = new File(args[2])
def json = new JsonSlurper()
def generated = new HashSet<String>()

def resourceId = { String id -> id.contains(':') ? id : "minecraft:$id".toString() }
def generatedId = { String id -> "matcha:almanac_silhouette/" + resourceId(id).replace(':', '/') }
def resourcePath = { String id, String kind, String extension ->
	def parts = resourceId(id).split(':', 2)
	"assets/${parts[0]}/$kind/${parts[1]}.$extension".toString()
}
def writeJson = { String path, Object value ->
	def file = new File(output, path)
	file.parentFile.mkdirs()
	file.setText(JsonOutput.prettyPrint(JsonOutput.toJson(value)) + '\n', 'UTF-8')
}

new ZipFile(args[1]).withCloseable { vanilla ->
	def readBytes = { String path ->
		def override = new File(resources, path)
		if (override.isFile()) {
			return override.bytes
		}
		def entry = vanilla.getEntry(path)
		if (entry == null) {
			throw new IllegalStateException("Missing silhouette source: $path")
		}
		vanilla.getInputStream(entry).withCloseable { it.bytes }
	}
	def readJson = { String path -> json.parse(readBytes(path)) }

	def texture = { String id ->
		def target = generatedId(id)
		def path = resourcePath(target, 'textures', 'png')
		if (generated.add(path)) {
			def sourcePath = resourcePath(id, 'textures', 'png')
			def image = ImageIO.read(new ByteArrayInputStream(readBytes(sourcePath)))
			if (image == null) {
				throw new IllegalStateException("Cannot decode silhouette source: $sourcePath")
			}
			def silhouette = new BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
			for (int y = 0; y < image.height; y++) {
				for (int x = 0; x < image.width; x++) {
					silhouette.setRGB(x, y, image.getRGB(x, y) & -16777216)
				}
			}
			def file = new File(output, path)
			file.parentFile.mkdirs()
			ImageIO.write(silhouette, 'png', file)
			def overrideMeta = new File(resources, sourcePath + '.mcmeta')
			if (overrideMeta.isFile() || vanilla.getEntry(sourcePath + '.mcmeta') != null) {
				new File(output, path + '.mcmeta').bytes = readBytes(sourcePath + '.mcmeta')
			}
		}
		target
	}

	Closure model
	model = { String id ->
		id = resourceId(id)
		if (id.startsWith('minecraft:builtin/')) {
			return id
		}
		def target = generatedId(id)
		def path = resourcePath(target, 'models', 'json')
		if (generated.add(path)) {
			def value = readJson(resourcePath(id, 'models', 'json'))
			if (value.parent) {
				value.parent = model(value.parent)
			}
			value.textures?.replaceAll { key, source -> source.startsWith('#') ? source : texture(source) }
			writeJson(path, value)
		}
		target
	}

	Closure itemModel
	itemModel = { Object value ->
		if (value instanceof Map) {
			if (value.type == 'minecraft:model') {
				value.model = model(value.model)
				value.tints = (value.tints ?: []).collect { [type: 'minecraft:constant', value: -16777216] }
			} else if (value.type == 'minecraft:special') {
				throw new IllegalStateException("Unsupported almanac special model: ${value.model.type}")
			}
			value.values().each { itemModel(it) }
		} else if (value instanceof List) {
			value.each { itemModel(it) }
		}
		value
	}

	def icons = new TreeSet<String>()
	def fish = json.parse(new File(resources, 'matcha/fish.json')).collect { "matcha:${it.id}".toString() }.toSet()
	fish.addAll(['minecraft:cod', 'minecraft:salmon', 'minecraft:pufferfish', 'minecraft:tropical_fish'])
	def almanac = new File(resources, 'data/matcha/advancement/anglers_almanac')
	almanac.eachFileRecurse { file ->
		if (file.isFile() && file.name.endsWith('.json') && file.name != 'root.json') {
			def icon = json.parse(file).display.icon
			if (resourceId(icon.id) in fish) {
				icons.add(resourceId(icon.components?.get('minecraft:item_model') ?: icon.id))
			}
		}
	}
	icons.each { id ->
		def value = readJson(resourcePath(id, 'items', 'json'))
		itemModel(value.model)
		writeJson(resourcePath(generatedId(id), 'items', 'json'), value)
	}
	println "Generated almanac silhouettes for ${icons.size()} item models"
}
