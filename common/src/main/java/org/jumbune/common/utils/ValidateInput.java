package org.jumbune.common.utils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jumbune.common.beans.DataValidationBean;
import org.jumbune.common.beans.Enable;
import org.jumbune.common.beans.FieldValidationBean;
import org.jumbune.common.beans.JobDefinition;
import org.jumbune.common.beans.Master;
import org.jumbune.common.beans.ProfilingParam;
import org.jumbune.common.beans.Slave;
import org.jumbune.common.beans.SupportedApacheHadoopVersions;
import org.jumbune.common.beans.Validation;
import org.jumbune.common.yaml.config.YamlConfig;
import org.jumbune.common.yaml.config.YamlLoader;
import org.jumbune.remoting.client.Remoter;
import org.jumbune.utils.beans.VirtualFileSystem;
import org.jumbune.utils.exception.JumbuneException;



/**
 * This class validates inputs from user and give messages if a field is not in correct format.
 */
public class ValidateInput {
	/** The suggestions. */
	private Map<String, Map<String,String>> suggestions = null;
	
	/** The failed validation. */
	private Map<String, Map<String,String>> failedValidation = null;
	
	/** The error messages. */
	private ErrorMessageLoader errorMessages = null;

	/** The j home. */
	private String jHome = null;
	
	/** The Constant REPORT_FROM_CLUSTER. */
	private static final String REPORT_FROM_CLUSTER = " dfsadmin -report | grep Name";
	
	private static final String HDFS_FILE_EXISTS = " fs -ls ";
	
	/** The Constant NEW_LINE. */
	private static final String NEW_LINE = "\n";
	
	/** Temp directory **/
	String TEMP_DIR = "tmp";
	
	/** Token file name ***/
	String TOKEN_FILE = "/jumbuneState.txt";
	
	/** The Constant LOGGER. */
	private static final Logger LOGGER = LogManager.getLogger(ValidateInput.class);

	private static final String DATA_VALIDATION_FIELD_LIST = "dataValidation.fieldValidationList[";

	/**
	 * constructor for initialise data member.
	 */
	public ValidateInput() {
		suggestions = new HashMap<String, Map<String,String>>();
		failedValidation = new HashMap<String, Map<String,String>>();
		errorMessages = ErrorMessageLoader.getInstance();
	}

	/**
	 * *
	 * it validates all field of yaml and return map which contains failhadooped and suggestion map.
	 *
	 * @param config object of yamlConfig class
	 * @return map containing failed and suggestion to given to user
	 */
	public Map<String, Map<String, Map<String, String>>> validateYaml(YamlConfig config) {
		Map<String, Map<String, Map<String,String>>> validateInput = new HashMap<String, Map<String, Map<String,String>>>();

		
		if (intialSettingsValidation(config)) {
			validateBasicField(config);

			if (isEnable(config.getEnableDataValidation())) {
				validateDataValidation(config);
			}

			if (isEnable(config.getDebugAnalysis())) {
				validateDebugField(config);
			}

		}
		if (!failedValidation.isEmpty()) {
			validateInput.put(Constants.FAILURE_KEY, failedValidation);
		}
		if (!suggestions.isEmpty()) {
			validateInput.put(Constants.SUGGESSION_KEY, suggestions);
		}
		return validateInput;
	}

	
	/**
	 * This method checks initial settings ex hadoop home is set or not or jumbune home is set or not and check if yaml is not empty and check if a
	 * module is enabled or not.
	 * 
	 * @param config
	 *            the config
	 * @return true if all case passes successfully
	 */
	private boolean intialSettingsValidation(YamlConfig config) {
		Map<String, String> listOfErrors = new HashMap<String, String>();
		boolean result = false;
		if (config != null) {
			jHome = YamlLoader.getjHome();
			if (!isAtleastOneModuleEnabled(config)) {
				listOfErrors.put("NO_MODULE_SELECT", errorMessages.get(ErrorMessages.NO_MODULE_SELECT));
			} else {
				if (!checkNullEmptyAndMessage(listOfErrors, jHome, ErrorMessages.JUMBUNE_HOME_NOT_SET, "JUMBUNE_HOME")
						&& checkFileOrDirExist(listOfErrors, jHome, ErrorMessages.JUMBUNE_HOME_NOT_SET, "JUMBUNE_HOME")) {
					result = checkProfilerState(config, listOfErrors);
				}

			}
		} else {
			listOfErrors.put("INVALID_YAML", errorMessages.get(ErrorMessages.INVALID_YAML));
		}
		if (!listOfErrors.isEmpty()) {
			failedValidation.put(Constants.BASIC_VALIDATION, listOfErrors);
		}
		return result;
	}

	/***
	 * It makes sure that if profiling job is already triggered then jumbune will not accept another profiling job.
	 * 
	 * @param config
	 * @param listOfErrors
	 * @return
	 */
	private boolean checkProfilerState(YamlConfig config, Map<String, String> listOfErrors) {
		boolean result = true;
		if (isEnable(config.getEnableStaticJobProfiling())) {
			String tokenFilePath = jHome + TEMP_DIR + TOKEN_FILE;
			File fToken = new File(tokenFilePath);
			if (fToken.exists()) {
				result = false;
				listOfErrors.put("PROFILING", errorMessages.get(ErrorMessages.PROFILER_ALREADY_RUNNING));
			} else {
				result = true;
			}
		}
		return result;
	}

	/**
	 * *
	 * This method checks that is given enum Value is TRUE or FALSE.
	 *
	 * @param enable is a enum
	 * @return true if it is TRUE or false if It is false or null
	 */
	public static boolean isEnable(Enable enable) {
		return (enable != null && Enable.TRUE.equals(enable) ? true : false);

	}

	/**
	 * *
	 * this method validate the inputs of debugging in jumbune.In web UI this field is in Debug Analysis tab
	 *
	 * @param config the config
	 */
	private void validateDebugField(YamlConfig config) {
		YamlLoader loader = new YamlLoader(config);
		Map<String,String> failedDebug = new HashMap<String,String>();
		Map<String,String> suggestionDebug = new HashMap<String,String>();
		/**
		if (loader.isInstrumentEnabled(Constants.DEBUG_IF_BLOCK) && (Constants.ZERO >= conf.getMaxIfBlockNestingLevel() || conf.getMaxIfBlockNestingLevel() >= Constants.DEBUG_MAX_IF)) {
				listofFailedDebug.add(errorMessages.get(ErrorMessages.DEB_IF_BLOCK));
		}
		
		if ((loader.isInstrumentEnabled(Constants.DEBUG_PARTITION_KEY))
				&& Constants.MIN_PARTITION_SAMPLE_INTER >= config.getPartitionerSampleInterval()) {

			listofFailedDebug.add(errorMessages.get(ErrorMessages.DEBUG_PARTITION_LEVEL_INVALID));
		}
		*/
		if (loader.isInstrumentEnabled(Constants.DEBUG_INSTR_REGEX_KEY)) {
			if (!config.getRegexValidations().isEmpty()) {
				checkFieldsValue(config.getRegexValidations(), ErrorMessages.DEBUG_REGEX_CLASS_INVALID, ErrorMessages.DEBUG_REGEX_KEY_INVALID,
						failedDebug,"regexValidations[");
			} else {
				failedDebug.put("debuggerConf.regexValidations",errorMessages.get(ErrorMessages.DEBUG_REGEX_VALIDATION_EMPTY));
			}

		}
		if (loader.isInstrumentEnabled(Constants.DEBUG_INST_USER_KEY)) {
			if (!config.getUserValidations().isEmpty()) {
				checkFieldsValue(config.getUserValidations(), ErrorMessages.DEBUG_INST_REGEX_INVALID, ErrorMessages.DEBUG_INST_KEY_INVALID,
						failedDebug,"regexValidations[");
			} else {
				failedDebug.put("debuggerConf.userValidations",errorMessages.get(ErrorMessages.DEBUG_USERDEFINE_VALIDATION_EMPTY));
			}
		}

		addToValidationList(Constants.DEBUGGER_VALIDATION, failedDebug, suggestionDebug);
	}

	/**
	 * *
	 * * In fields of debugger it validates regex userdefine validations.
	 *
	 * @param validations list of field which is to be validated
	 * @param classKey value in class field
	 * @param regexKey value of regex field
	 * @param failedValidation list of validation which is failed
	 */
	private void checkFieldsValue(List<Validation> validations, int classKey, int regexKey, Map<String,String> failedValidation,String fieldValue) {
		int count=0;
		for (Validation validation : validations) {
			if (isNullOrEmpty(validation.getClassname())) {
				failedValidation.put(fieldValue+count+"].classname",errorMessages.get(classKey));
			}
			if (isNullOrEmpty(validation.getValue()) && isNullOrEmpty(validation.getKey())) {
				failedValidation.put(fieldValue+count+"].value",errorMessages.get(regexKey));
			}
			count++;
		}

	}

	/**
	 * *
	 * if(config.getHadoopJobProfile()!=null && Enable.TRUE.equals(config.getHadoopJobProfile())) {
	 * 
	 * this mehod validate the data validation inputs from user .In web UI this field is in data validation tab
	 *
	 * @param config the config
	 */

	private void validateDataValidation(YamlConfig config) {
		Map<String,String> failedDataValidation = new HashMap<String,String>();
		Map<String,String> listOfSuggestions = new HashMap<String,String>();

		if (config.getDataValidation() != null) {
			DataValidationBean dataValidationBean = checkAndValidateHdfsPath(
					config, failedDataValidation);

			
			checkIfFieldAndRecordSeparatorAreNull(failedDataValidation,
					dataValidationBean);
			int countForFieldValidation = 0;
			// TODO:  if fieldvalidation list is empty
			List<FieldValidationBean> bean = config.getDataValidation().getFieldValidationList();
			if (bean != null) {
				for (FieldValidationBean fielValidationBean : bean) {
					if (fielValidationBean.getFieldNumber() >= Constants.ZERO) {
						countForFieldValidation = fielValidationBean.getFieldNumber() + 1;
						if (isNullOrEmpty(fielValidationBean.getNullCheck()) && isNullOrEmpty(fielValidationBean.getDataType())
								&& isNullOrEmpty(fielValidationBean.getRegex())) {
							listOfSuggestions
									.put(DATA_VALIDATION_FIELD_LIST+(countForFieldValidation-1)+"].regex",MessageFormat.format(errorMessages.get(ErrorMessages.DVALID_FIELD_NO_VAL), countForFieldValidation));
						} else {
							// check null check
							checkEmptyAndShowMessage(failedDataValidation, fielValidationBean.getNullCheck(), ErrorMessages.DVALID_NULL_CHECK,
									countForFieldValidation,DATA_VALIDATION_FIELD_LIST+(countForFieldValidation-1)+"].nullCheck");
							// check data type value
							checkEmptyAndShowMessage(listOfSuggestions, fielValidationBean.getDataType(), ErrorMessages.DVALID_DATA_TYPE,
									countForFieldValidation,DATA_VALIDATION_FIELD_LIST+(countForFieldValidation-1)+"].dataType");
							// check regex expression
							checkEmptyAndShowMessage(listOfSuggestions, fielValidationBean.getRegex(), ErrorMessages.DVALID_REGEX,
									countForFieldValidation,DATA_VALIDATION_FIELD_LIST+(countForFieldValidation-1)+"].regex");
						}
					}
				}
			} else {
				failedDataValidation.put("dataValidation.fieldValidationList",errorMessages.get(ErrorMessages.DV_NO_FIELD_TO_VARIFY));
			}

			
		} else {
			failedDataValidation.put("dataValidation",errorMessages.get(ErrorMessages.DATA_VALID_INVALID));
		}

		addToValidationList(Constants.DATA_VALIDATE, failedDataValidation, listOfSuggestions);
	}

	/**
	 * Check if field and record separator are null.
	 *
	 * @param listOfFailedValidation contains the list of failed validation.
	 * @param dataValidationBean contains all validation checks to be applied by the user.
	 */
	private void checkIfFieldAndRecordSeparatorAreNull(
			Map<String,String> listOfFailedValidation,
			DataValidationBean dataValidationBean) {
		if (dataValidationBean.getFieldSeparator() == null) {
			listOfFailedValidation.put("dataValidation.fieldSeparator",errorMessages.get(ErrorMessages.DVALID_FIELD_S_EMPTY));
		}
		

		if (dataValidationBean.getRecordSeparator() == null) {
			listOfFailedValidation.put("dataValidation.recordSeparator",errorMessages.get(ErrorMessages.DVALID_RECORD_S_EMPTY));
		}
	}

	/**
	 * Check and validate hdfs path.
	 *
	 * @param config bean for the yaml file
	 * @param listOfFailedValidation contains a list of failed validation in case of HDFS.
	 * @return all validation checks to be applied by the user
	 */
	private DataValidationBean checkAndValidateHdfsPath(YamlConfig config,
			Map<String,String> listOfFailedValidation) {
		DataValidationBean dataValidationBean = config.getDataValidation();
		String hadoopInputPath = config.getHdfsInputPath();
		if (!checkNullEmptyAndMessage(listOfFailedValidation, hadoopInputPath, ErrorMessages.HDFS_FIELD_INVALID,"hdfsInputPath")) {
			try {
				if (!isHadoopInputPath(hadoopInputPath, config)) {
					listOfFailedValidation.put("hdfsInputPath",errorMessages.get(ErrorMessages.HADOOP_INPUT_PATH_INVALID));
				}
			} catch (JumbuneException e) {
				listOfFailedValidation.put("dataValidation",errorMessages.get(ErrorMessages.HADOOP_NOT_EXIST));
			}

		}
		return dataValidationBean;
	}

	/**
	 * *
	 * this method validate the job jar information .In web UI this field is in job tab
	 *
	 * @param config the config
	 */
	private void validateJobs(YamlConfig config,Map<String,String> failedCases,Map<String,String> suggestion) {

		int countForJobJar = 0;
		// TODO:  - what if jobs are null!!
		if (!config.getJobs().isEmpty()) {
			for (JobDefinition jobDefinition : config.getJobs()) {

				countForJobJar++;
				if (!isEnable(config.getIncludeClassJar())) {
					checkEmptyAndShowMessage(failedCases, jobDefinition.getJobClass(), ErrorMessages.JOB_CLASS_NAME_INVALID, countForJobJar,"jobs["+countForJobJar+"].jobClass");
				}
				checkEmptyAndShowMessage(failedCases, jobDefinition.getName(), ErrorMessages.JOB_JAR_NAME_INVALID, countForJobJar,"jobs["+countForJobJar+"].name");

				checkEmptyAndShowMessage(suggestion, jobDefinition.getParameters(), ErrorMessages.JOB_PARAMETER_INVALID, countForJobJar,"jobs["+countForJobJar+".parameters");

			}
		} else {
			failedCases.put("jobs",errorMessages.get(ErrorMessages.JOBS_FIELD_ENPTY));
		}
	}


	/**
	 * *
	 * this method validates the very basic entries of jumbune. In web User Interface these entries in basic tab
	 *
	 * @param config the config
	 */
	private void validateBasicField(YamlConfig config) {
		Map<String,String> failedCases = new HashMap<String,String>();
		Map<String,String> suggestionList = new HashMap<String,String>();
		
		checkIfJumbuneJobEmptyOrNot(config, failedCases,"jumbuneJobName");
		
		if (isEnable(config.getDebugAnalysis()) || isEnable(config.getEnableStaticJobProfiling())) {
			/**
			 * check if slave jumbune home is empty
			 */

			checkNullEmptyAndMessage(failedCases, config.getsJumbuneHome(), ErrorMessages.BASIC_SLAVE_HOME_EMPTY,"");
			/**
			 * check master host user name is nulll or conatain space
			 */
			Master master = config.getMaster();
			checkMasterNodeValidation(failedCases, master);
			if (!config.getSlaves().isEmpty() && !failedCases.containsValue(errorMessages.get(ErrorMessages.RSA_DSA_INVALID))) {
				validateSlaveField(failedCases, config);
			}
			checkMrJobField(config);

			
		}
		addToValidationList(Constants.BASIC_VALIDATION, failedCases, suggestionList);
	}

	/**
	 * Check master node validation.
	 *
	 * @param failedCases error list
	 * @param master the master
	 */
	private void checkMasterNodeValidation(Map<String,String> failedCases,
			Master master) {
		if (master != null) {
			checkNullEmptyAndMessage(failedCases, master.getUser(), ErrorMessages.MASTER_HOST_USER,"master.user");

			/***
			 * master host validation
			 */
			checkIPAndShowMessage(failedCases, master.getHost(), ErrorMessages.MASTER_HOST_IP,"master.host");

			checkRsaDsaFileExistence(failedCases, master,"master.rsaFile");
		} else {
			failedCases.put("master.user",errorMessages.get(ErrorMessages.MASER_FIELD_INVALID));
		}
	}

	/**
	 * Check mr job field.
	 *
	 * @param config object of yamlConfig class
	 * @param failedCasesList error list
	 */
	private void checkMrJobField(YamlConfig config) {
		Map<String,String> failedCases=new HashMap<String, String>();
		Map<String,String> suggetion=new HashMap<String, String>();
		if (isEnable(config.getEnableStaticJobProfiling()) || config.getDebugAnalysis().getEnumValue()) {
			checkNullEmptyAndMessage(failedCases, config.getInputFile(), ErrorMessages.BASIC_INPUT_PATH_EMPTY,"inputFile");
			validateJobs(config,failedCases,suggetion);
		}
		addToValidationList(Constants.JOBS_VALIDATION,failedCases,suggetion );
	}

	/**
	 * Check rsa dsa file existence.
	 *
	 * @param failedCases error list
	 * @param master the master
	 * @param fieldValue
	 */
	private void checkRsaDsaFileExistence(Map<String,String> failedCases,
			Master master,String fieldValue) {
		/**
		 * RSA file and DSA file existence validation
		 */
		String hostMaster = master.getHost();
		Remoter remoter = new Remoter(hostMaster, Integer.valueOf(master.getAgentPort()));
		StringBuilder lsRsa = new StringBuilder().append(Constants.LS_COMMAND).append(Constants.SPACE).append(master.getRsaFile());
		
		CommandWritableBuilder builder = new CommandWritableBuilder();
		builder.addCommand(lsRsa.toString(), false, null);
		String responseRsa = (String) remoter.fireCommandAndGetObjectResponse(builder.getCommandWritable());
		String rsaResponse = responseRsa.substring(0, responseRsa.length()-1);
		
		StringBuilder  lsDsa = new StringBuilder().append(Constants.LS_COMMAND).append(Constants.SPACE).append(master.getDsaFile());
		builder.getCommandBatch().clear();
		builder.addCommand(lsDsa.toString(), false, null);
		String responseDsa = (String) remoter.fireCommandAndGetObjectResponse(builder.getCommandWritable());
		remoter.close();
		String dsaResponse = responseDsa.substring(0, responseDsa.length()-1);
		if (!(master.getRsaFile() != null && rsaResponse.equalsIgnoreCase(master.getRsaFile()))
				&& !(master.getDsaFile() != null && dsaResponse.equalsIgnoreCase(master.getDsaFile()))) {
			failedCases.put(fieldValue,errorMessages.get(ErrorMessages.RSA_DSA_INVALID));
		}
	}

	/**
	 * Check if jumbune job empty or not.
	 *
	 * @param config object of yamlConfig class
	 * @param failedCases error list
	 * @param fieldValue
	 */
	private void checkIfJumbuneJobEmptyOrNot(YamlConfig config,
			Map<String,String> failedCases,String fieldValue) {
		/**
		 * check if jumbune job name is empty or not
		 */

		if (!checkNullEmptyAndMessage(failedCases, config.getFormattedJumbuneJobName(), ErrorMessages.JUMBUNE_JOB_NAME_BLANK,fieldValue)) {
			String jumbuneJobDirectoryPath = YamlLoader.getJobJarLoc() + config.getFormattedJumbuneJobName() + "/logs/";
			if (new File(jumbuneJobDirectoryPath).exists()) {
				failedCases.put(fieldValue,errorMessages.get(ErrorMessages.BASIC_JOB_NAME_EXIST));
			}
		}
	}

	/**
	 * This method validates Slave information in Basic field Tab in Web UI, and check slaves input is in correct format or not.
	 *
	 * @param failedCases error list
	 * @param yConfig the y config
	 */
	private void validateSlaveField(Map<String,String> failedCases, YamlConfig yConfig) {
		int count = 0;
		List<String> listOfValidDataNode = new ArrayList<String>();
		List<Slave> slaves = yConfig.getSlaves();
		String commandResponse = RemotingUtil.fireCommandOnSupporteHadoopVersionAndGetStringResponse(yConfig, REPORT_FROM_CLUSTER);
		String[] splitArray = commandResponse.split(NEW_LINE);
		for (String line : splitArray) {
			listOfValidDataNode.add(line.split(":")[1].trim());
		}
		for (Slave slave : slaves) {
			count++;
			checkEmptyAndShowMessage(failedCases, slave.getUser(), ErrorMessages.SLAVE_USER_INVALID, count,"slaves["+count+"].user");
			List<String> hosts = new ArrayList<String>();
			for (String str : slave.getHosts()) {
				hosts.add(str);
			}
			if (hosts != null) {
				int countForHost = 0;
				for (String slaveHost : hosts) {
					countForHost++;
					if (isNullOrEmpty(slaveHost) || !(listOfValidDataNode.contains(slaveHost))) {
						Integer[] numbers = new Integer[] { count, countForHost };
						failedCases.put("slaves["+(count-1)+"].hosts["+(countForHost-1)+"]",MessageFormat.format(errorMessages.get(ErrorMessages.SLAVE_HOST_INVALID), numbers));
					}
				}
			} else {
				failedCases.put("slaves.host",errorMessages.get(ErrorMessages.SLAVE_HOST_FIELD_NULL));
			}
			// Adding slave range for host

			if (Constants.ON.equalsIgnoreCase(slave.getEnableHostRange())) {
				String fromIP[] = slave.getHostRangeFromValue().split("\\.");
				String toIP[] = slave.getHostRangeToValue().split("\\.");
				String hostRangeVal = fromIP[0] + Constants.DOT + fromIP[1] + Constants.DOT + fromIP[2];
				int fromRangeVal = Integer.parseInt(fromIP[fromIP.length - 1]);
				int toRangeVal = Integer.parseInt(toIP[toIP.length - 1]);
				if (toRangeVal < fromRangeVal) {
					failedCases.put("slaves["+(count-1)+"].hostRangeToValue",errorMessages.get(ErrorMessages.SLAVE_HOST_RANGE_FROM_GREATER_THAN_TO));
				} else {
					addSlaveRanges(fromRangeVal, toRangeVal, hostRangeVal, hosts);
				}
			}

			slave.setHosts(hosts.toArray(new String[hosts.size()]));
		}
	}

	

	
	/**
	 * *
	 * 
	 * Add the given list to map if its not empty.
	 *
	 * @param key the key
	 * @param failureList the failure list
	 * @param suggestionList the suggestion list
	 */
	private void addToValidationList(String key, Map<String,String> failureList, Map<String,String> suggestionList) {
		if (!failureList.isEmpty()) {
			failedValidation.put(key, failureList);
		}
		if (!suggestionList.isEmpty()) {
			suggestions.put(key, suggestionList);
		}
	}

	

	/**
	 * This method check null or space and show message to user.
	 *
	 * @param errorList add error message to list
	 * @param value is string in which validation is applied
	 * @param errorCode code of error which is a integer
	 * @param fieldName for field 
	 * @return true, if successful
	 */
	private boolean checkNullEmptyAndMessage(Map<String,String> errorList, String value, int errorCode,String fieldName) {
		if (isNullOrEmpty(value)) {
			errorList.put(fieldName,errorMessages.get(errorCode));
			return true;
		}
		return false;
	}

	/**
	 * This method check ip and show message to user.
	 *
	 * @param listOfError add error message to list
	 * @param value is string in which validation is applied
	 * @param errorCode code of error which is a integer
	 * @param filedValue
	 */
	private void checkIPAndShowMessage(Map<String,String> listOfError, final String value, int errorCode,String fieldValue) {
		if (isNullOrEmpty(value) || !checkIPAdress(value)) {
			listOfError.put(fieldValue,errorMessages.get(errorCode));
		}
	}

	/**
	 * This method check file existence and show message to user.
	 *
	 * @param listOfError add error message to list
	 * @param value is string in which validation is applied
	 * @param errorCode code of error which is a integer
	 * @return the boolean
	 */
	private Boolean checkFileOrDirExist(Map<String,String> listOfError, final String value, int errorCode,String fieldValue) {
		if (!isNullOrEmpty(value) && !new File(value).exists()) {
			listOfError.put(fieldValue,errorMessages.get(errorCode));
			return false;
		}
		return true;
	}

	/**
	 * This method check null or space and show message to user.
	 *
	 * @param listOfError add error message to list
	 * @param value is string in which validation is applied
	 * @param errorCode code of error which is a integer
	 * @param fieldIndex which is replace the value in message
	 */
	private void checkEmptyAndShowMessage(Map<String,String> listOfError, String value, int errorCode, int fieldIndex,String fieldValue) {
		if (isNullOrEmpty(value)) {
			listOfError.put(fieldValue,MessageFormat.format(errorMessages.get(errorCode), fieldIndex));
		}
	}

	/**
	 * check if a command is exist or not it returns true if it is exist import
	 * 
	 *
	 * @param value the value
	 * @param inputValue value which is to be tested
	 * @return true if command exist already
	 */
	public Boolean checkCommand(int value, String inputValue) {
		String[] commandArray = errorMessages.get(value).split("\\\n");
		for (String string : commandArray) {
			if (string.equals(string)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if is hadoop input path provided exists on HDFS or not.
	 *
	 * @param path the path
	 * @param config the config
	 * @return true, if is hadoop input path
	 * @throws JumbuneException the hTF exception
	 */
	public boolean isHadoopInputPath(String path, YamlConfig config) throws JumbuneException {
		YamlLoader loader = new YamlLoader(config);
		boolean isExists = false;
		VirtualFileSystem fs = null;
		try {
			if(!SupportedApacheHadoopVersions.HADOOP_2_0_CDH.equals(RemotingUtil.getHadoopVersion(config))){
				fs = RemotingUtil.getVirtualFileSystem(loader);
				isExists = fs.exists(path);
			}else{
				LOGGER.debug("Processing for CDH..firing command: "+HDFS_FILE_EXISTS+path);
				String commandResponse = RemotingUtil.fireCommandOnSupporteHadoopVersionAndGetStringResponse(config, HDFS_FILE_EXISTS + path);
				LOGGER.debug("command response is:  "+commandResponse);
				if(!"".equals(commandResponse)){
					return true;
				}
				return false;
			}
		} catch (IOException e) {
			LOGGER.error("An input output error has been occured while validate hdfs input path");
		} finally {
			try {
				if(fs!=null){
				fs.close();
				}
			} catch (IOException e) {
				LOGGER.error("An input output error has been occured while closing Hadoop IO channel");
			}
		}
		return isExists;
	}

	/**
	 * Checks if is atleast one module enabled.
	 *
	 * @param config the config
	 * @return true, if is atleast one module enabled
	 */
	private boolean isAtleastOneModuleEnabled(YamlConfig config) {
		boolean result = false;
		boolean isProfiling = isProfilingModuleEnabled(config);
		if (isEnable(config.getEnableDataValidation()) || isEnable(config.getHadoopJobProfile()) || isEnable(config.getDebugAnalysis())
				|| isProfiling) {
			result = true;
		}
		return result;

	}

	/**
	 * Checks if  profiling module enabled.
	 *
	 * @param config the config
	 * @return true, if profiling module is enabled
	 */
	private boolean isProfilingModuleEnabled(YamlConfig config) {
		boolean result = false;
		if (isEnable(config.getEnableStaticJobProfiling())) {
			result = true;
		}
		return result;

	}

	/**
	 * This method check port is available or not if avaliable it returns true.
	 *
	 * @param port is port number which is to be check
	 * @param inetAddress the inet address
	 * @return true if port is available
	 */
	public boolean isPortAvailable(int port, String inetAddress) {
		Socket socket = null;
		try {
			socket = new Socket();
			socket.connect(new InetSocketAddress(inetAddress, port));
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				return false;
			}
		}
	}

	/**
	 * Check port availablity.
	 *
	 * @param config the config
	 * @param profilingParam the profiling param
	 * @return true, if successful
	 */
	public boolean checkPortAvailablity(YamlConfig config, ProfilingParam profilingParam) {
		int count = 0;
		for (Slave slave : config.getSlaves()) {
			count=count+1;
			if (slave.getHosts() != null) {
				for (String slaveHost : slave.getHosts()) {
					if (isPortAvailable(Integer.parseInt(profilingParam.getDataNodeJmxPort()), slaveHost)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Check port availablity task.
	 *
	 * @param config the config
	 * @param profilingParam the profiling param
	 * @return true, if successful
	 */
	public boolean checkPortAvailablityTask(YamlConfig config, ProfilingParam profilingParam) {
		int count = 0;
		for (Slave slave : config.getSlaves()) {
			count=count+1;
			if (slave.getHosts() != null) {
				for (String slaveHost : slave.getHosts()) {
					if (isPortAvailable(Integer.parseInt(profilingParam.getTaskTrackerJmxPort()), slaveHost)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	/**
	 * This method checks whether a given string is null or empty.
	 *
	 * @param str given string
	 * @return true if the given string is null or empty
	 */
	public static boolean isNullOrEmpty(String str) {
		if (str == null) {
			return true;
		}
	
		if (str.trim().length() == 0) {
			return true;
		}
	
		return false;
	}
	/**
	 * Adds the slave ranges.
	 *
	 * @param fromRangeVal the from range val
	 * @param toRangeVal the to range val
	 * @param hostRangeValue the host range value
	 * @param hosts the hosts
	 */
	public static void addSlaveRanges(int fromRangeVal, int toRangeVal,
			String hostRangeValue, List<String> hosts) {

		String hostRangeValFinal;
		for (int ipCount = fromRangeVal; ipCount <= toRangeVal; ipCount++) {
			hostRangeValFinal = hostRangeValue + Constants.DOT + ipCount;
			if (checkIPAdress(hostRangeValFinal)
					&& !hosts.contains(hostRangeValFinal)) {
				hosts.add(hostRangeValFinal);
			}
		}

	}
	
	/**
	 * check whether given ip address is valid or not.
	 *
	 * @param ipaddress the ipaddress
	 * @return true if ip adress is valid
	 */
	public static Boolean checkIPAdress(String ipaddress) {
		try {
			return InetAddress.getByName(ipaddress).isReachable(Constants.FIVE_HUNDRED);
		} catch (Exception e) {
			return false;
		}
	}
	
	

}