package com.edlio.emailreplyparser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Email {
	private List<Fragment> fragments = new ArrayList<Fragment>();
	
	public Email(List<Fragment> fragments) {
		this.fragments.addAll(fragments);
	}
	
	public List<Fragment> getFragments() {
		return fragments;
	}
	
	public String getVisibleText() {
		List<String> visibleFragments = new ArrayList<String>();
		for (Fragment fragment : fragments) {
			if (!fragment.isHidden())
				visibleFragments.add(fragment.getContent());
		}
		return StringUtils.stripEnd(StringUtils.join(visibleFragments,"\n"), null);
	}
	
	public String getHiddenText() {
		List<String> hiddenFragments = new ArrayList<String>();
		for (Fragment fragment : fragments) {
			if (fragment.isHidden())
				hiddenFragments.add(fragment.getContent());
		}
		return StringUtils.stripEnd(StringUtils.join(hiddenFragments,"\n"), null);
	}
	
}
