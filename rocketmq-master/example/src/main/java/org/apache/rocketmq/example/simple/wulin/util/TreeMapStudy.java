package org.apache.rocketmq.example.simple.wulin.util;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class TreeMapStudy {
	
	public static void main(String[] args) {
		TreeMapStudy treeMapStudy = new TreeMapStudy();
		
		TreeMap<Long, String> treeMap = treeMapStudy.getTreeMap();
		
		Set<Entry<Long, String>> entrySet = treeMap.entrySet();
		for (Entry<Long, String> entry : entrySet) {
			System.out.println(entry.getKey()+":"+entry.getValue());
		}
		
		System.out.println();
		
		
	}
	
	private TreeMap<Long,String> getTreeMap(){
		TreeMap<Long,String> treeMap = new TreeMap<Long,String>(new Comparator<Long>() {
			@Override
			public int compare(Long o1, Long o2) {
				return (int) (o2-o1);
			}
		});
		treeMap.put(10L, "10");
		treeMap.put(1L, "1");
		treeMap.put(5L, "5");
		treeMap.put(3L, "3");
		treeMap.put(8L, "8");
		
		return treeMap;
	}

}
