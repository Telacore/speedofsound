VERSION = v1.13.2

# Expected SHA256 checksums (update these from the checksum.txt file)
CHECKSUM_JAR = 772f04285e57f854b60847c5e5b8b93debaee68fdec644fb49c331c18a083443
CHECKSUM_AARCH64 = ed11e9caff63d73fac03e3ecc026dac645a2a340b4a0f5b9076d4ab3e1c7b39e
CHECKSUM_X64 = 6b9e96bc9d13ca9080eeb65010d5c3cbed1b29848a3b890ad62e2c7f31072eb7

download-checksum:
	@echo "Downloading checksum.txt file."
	rm -f checksum.txt
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/$(VERSION)/checksum.txt
	cat checksum.txt | grep jar

download-libs:
	@echo "Removing existing JAR files..."
	rm -f sherpa-onnx-*.jar
	@echo "Downloading JAR files..."
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/$(VERSION)/sherpa-onnx-$(VERSION).jar
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/$(VERSION)/sherpa-onnx-native-lib-linux-aarch64-$(VERSION).jar
	wget https://github.com/k2-fsa/sherpa-onnx/releases/download/$(VERSION)/sherpa-onnx-native-lib-linux-x64-$(VERSION).jar
	@echo "Verifying checksums..."
	@echo "$(CHECKSUM_JAR)  sherpa-onnx-$(VERSION).jar" | sha256sum -c -
	@echo "$(CHECKSUM_AARCH64)  sherpa-onnx-native-lib-linux-aarch64-$(VERSION).jar" | sha256sum -c -
	@echo "$(CHECKSUM_X64)  sherpa-onnx-native-lib-linux-x64-$(VERSION).jar" | sha256sum -c -
	@echo "All checksums verified."
