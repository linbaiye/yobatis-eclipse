/*
 *    Copyright 2018 the original author or authors.
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *    use this file except in compliance with the License.  You may obtain a copy
 *    of the License at
 *    
 *      http://www.apache.org/licenses/LICENSE-2.0
 *    
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *    License for the specific language governing permissions and limitations under
 *    the License.
 */
package org.nalby.yobatis.mybatis;

import java.io.InputStream;
import java.util.List;
import org.mybatis.generator.api.GeneratedFile;
import org.mybatis.generator.api.GeneratedJavaFile;
import org.mybatis.generator.api.GeneratedXmlFile;
import org.mybatis.generator.api.LibraryRunner;
import org.mybatis.generator.api.YobatisJavaFile;
import org.nalby.yobatis.exception.InvalidMybatisGeneratorConfigException;
import org.nalby.yobatis.log.LogFactory;
import org.nalby.yobatis.log.Logger;
import org.nalby.yobatis.structure.File;
import org.nalby.yobatis.structure.Project;
import org.nalby.yobatis.util.Expect;
import org.nalby.yobatis.util.FolderUtil;
import org.nalby.yobatis.xml.SqlMapperParser;

/**
 * Write files generated by MyBatis Generator to corresponding directories.
 * @author Kyle Lin
 */
public class MybatisFilesWriter {

	private LibraryRunner runner;

	private Project project;
	
	private Logger logger = LogFactory.getLogger(MybatisFilesWriter.class);
	
	@FunctionalInterface
	private interface FileSelector {
		boolean select(GeneratedFile file);
	}

	public MybatisFilesWriter(Project project, LibraryRunner mybatisRunner) {
		Expect.notNull(project, "project must not be null.");
		Expect.notNull(mybatisRunner, "mybatisRunner must not be null.");
		this.project = project;
		this.runner = mybatisRunner;
		if (runner.getGeneratedJavaFiles() == null) {
			throw new InvalidMybatisGeneratorConfigException("No java files generated.");
		}
		if (runner.getGeneratedXmlFiles() == null) {
			throw new InvalidMybatisGeneratorConfigException("No xml files generated.");
		}
	}
	

	private void writeFile(String path, String content, boolean overwrite) {
		File file = project.findFile(path);
		if (file == null || overwrite) {
			file = project.createFile(path);
			file.write(content);
		}
	}

	private void writeJavaFile(GeneratedJavaFile javafile, boolean overwrite) {
		String dirpath = FolderUtil.concatPath(javafile.getTargetProject(), 
				javafile.getTargetPackage().replaceAll("\\.", "/"));
		String filepath = FolderUtil.concatPath(dirpath, javafile.getFileName());
		writeFile(filepath, javafile.getFormattedContent(), overwrite);
	}
	
	private void writeJavaFiles() {
		for (GeneratedJavaFile tmp : runner.getGeneratedJavaFiles()) {
			if (!(tmp instanceof YobatisJavaFile)) {
				continue;
			}
			YobatisJavaFile javaFile = (YobatisJavaFile)tmp;
			if (javaFile.isOverwrite()) {
				writeJavaFile(javaFile, true);
			} else {
				writeJavaFile(javaFile, false);
			}
		}
	}
	
	private String mergeManualSqlXml(String path, String content) {
		File file = project.findFile(path);
		if (file != null) {
			try (InputStream inputStream = file.open()) {
				SqlMapperParser oldXml = new SqlMapperParser(inputStream);
				SqlMapperParser newXml = SqlMapperParser.fromString(content);
				newXml.merge(oldXml);
				return newXml.toXmlString();
			} catch (Exception e) {
				//Do nothing.
			}
		}
		try {
			// Transform format.
			return SqlMapperParser.fromString(content).toXmlString();
		} catch (Exception e) {
		}
		return content;
	}
	
	private void writeXmlFiles() {
		List<GeneratedXmlFile> xmlFiles = runner.getGeneratedXmlFiles();
		for (GeneratedXmlFile xmlfile : xmlFiles) {
			String dirpath = FolderUtil.concatPath(xmlfile.getTargetProject(), 
					xmlfile.getTargetPackage().replaceAll("\\.", "/"));
			String filepath = FolderUtil.concatPath(dirpath, xmlfile.getFileName());
			String content = mergeManualSqlXml(filepath, xmlfile.getFormattedContent());
			writeFile(filepath, content, true);
		}
	}

	public void writeAll() {
		writeJavaFiles();
		writeXmlFiles();
		logger.info("Files have been generated, happy coding.");
	}

}
