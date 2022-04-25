/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnipreprocessing.plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class LogMasker {

	private final MaskingRuleClass maskingRuleClass;
	public RegexMasker masker;
    
    public LogMasker() {
        maskingRuleClass = new MaskingRuleClass();
        masker = new RegexMasker(maskingRuleClass.rules, maskingRuleClass.rulesBeforeSplit);
    }

    public String mask(String content, boolean isControlPlaneLog) {
    	return masker.mask(content, isControlPlaneLog);
    }


    private class RegexMasker {

        List<MaskingRule> maskingRules, maskingRulesBeforeSplit;
        String delimiters, removeDelimiters;
        Pattern ansiEscape;
    	public RegexMasker(List<MaskingRule> maskingRules, List<MaskingRule> maskingRulesBeforeSplit ) {
    		this.maskingRules = maskingRules;
    		this.maskingRulesBeforeSplit = maskingRulesBeforeSplit;
            this.delimiters = "([|:| \\(|\\)|\\[|\\]\'|\\{|\\}|\"|,|=])";
            this.removeDelimiters = "([| \\(|\\)|\\[|\\]\'|\\{|\\}|\"|,])";
            this.ansiEscape = Pattern.compile("(\\x9B|\\x1B\\[)[0-?]*[ -\\/]*[@-~]");
    	}

    	public String mask(String content, boolean isControlPlaneLog) {
    		String maskedContent = ansiEscape.matcher(content).replaceAll("");
    		// if isControlPlaneLog == True // extra log for well structured CP logs
    		for (MaskingRule mi : maskingRulesBeforeSplit) {
                maskedContent = mi.regexPattern.matcher(maskedContent).replaceAll(mi.maskWithWrap);
    		}

            maskedContent = String.join(" ", maskedContent.split("((?==|\\||:)|(?<==|\\||:))")); //jave has issue
            maskedContent = String.join(" ", maskedContent.split("[\n\r\t\r]"));

            for (MaskingRule mi : maskingRules) {
                maskedContent = mi.regexPattern.matcher(maskedContent).replaceAll(mi.maskWithWrap);
    		}

    		List<String> splitContext = Arrays.asList(maskedContent.split(delimiters));

            maskedContent = String.join(" " , splitContext.stream().filter(s -> !removeDelimiters.contains(s)).collect(Collectors.toList()));// TODO removeDlimiters.contains???

    		maskedContent = maskedContent.toLowerCase();
    		return maskedContent;
    	}
    }
    public class MaskingRuleClass {
        List<MaskingRule> rules = new ArrayList<>();
        List<MaskingRule> rulesBeforeSplit = new ArrayList<>();

        public MaskingRuleClass() {
            rules.add(new MaskingRule("[^\\s]+\\.go : [0-9]+", "GO_FILE_PATH"));
            rules.add(new MaskingRule("[a-z0-9]+[\\._]?[a-z0-9]+[@]\\w+[.]\\w{2,3}", "EMAIL_ADDRESS"));
            rules.add(new MaskingRule("((?<=[^A-Za-z0-9])|^)(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\/\\d{1,3})((?=[^A-Za-z0-9])|$)", "IP"));
            rules.add(new MaskingRule("((?<=[^A-Za-z0-9])|^)(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})((?=[^A-Za-z0-9])|$)", "IP"));
            rules.add(new MaskingRule("((?<=[^A-Za-z0-9])|^)(\\d+\\.\\d+\\s*(s|ds|cs|ms|µs|ns|ps|fs|as|zs|ys))((?=[^A-Za-z0-9])|$)", "DURATION"));
            rules.add(new MaskingRule("(/[a-zA-Z_\\-\\./\\(?:[0-9]+[a-zA-Z0-9]|[a-zA-Z]+[0-9]\\)]*[\\s]?)","PATH"));
            rules.add(new MaskingRule("(?:[0-9]+[a-zA-Z!\\(\\)\\-\\.\\?\\[\\]_`~;:!@#$%^&+=\\*]|[a-zA-Z!\\(\\)\\-\\.\\?\\[\\]_`~;:!@#$%^&+=\\*]+[0-9])[a-zA-Z0-9!\\(\\)\\-\\.\\?\\[\\]_`~;:!@#$%^&+=\\*]*","TOKEN_WITH_DIGIT"));
            rules.add(new MaskingRule("((?<=[^A-Za-z0-9])|^)([\\-\\+]?\\d*\\.?\\d+)((?=[^A-Za-z0-9])|$)","NUM"));
            rules.add(new MaskingRule("\\{[\\s]*\\}","EMPTY_SET"));
            rules.add(new MaskingRule("\\[[\\s]*\\]", "EMPTY_LIST"));

            rulesBeforeSplit.add(new MaskingRule("(http|ftp|https)://([\\w_-]+(?:(?:\\.*[\\w_-]+)+))([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?", "URL"));
            rulesBeforeSplit.add(new MaskingRule("\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[1-2]\\d|3[0-1])[T|\\s](?:[0-1]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+|)[(?:Z|(?:\\+|\\-)(?:\\d{2}):?(?:\\d{2}))]", "UTC_DATE"));
            rulesBeforeSplit.add(new MaskingRule("[IWEF]\\d{4}\\s\\d{2}:\\d{2}:\\d{2}[\\.\\d+]*", "KLOG_DATE"));
            rulesBeforeSplit.add(new MaskingRule("(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(\\d{1,2}) (2[0-3]|[01]?[0-9]):([0-5]?[0-9]):([0-5]?[0-9])", "CUSTOM_DATE"));
        }
        
    }
    public class MaskingRule {
            
        String regexPatternString, maskWith, maskWithWrap;
        Pattern regexPattern;

        public MaskingRule(String regexPatternString, String maskWith) {
            this.regexPatternString = regexPatternString;
            this.maskWith = maskWith;
            this.maskWithWrap = "<" + maskWith + ">";
            this.regexPattern = Pattern.compile(regexPatternString);
        }
    }

}