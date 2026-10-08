package com.kocaaslan.istakip;

import java.util.*;

/** Explicit legacy-import reversal. Content alone never identifies an archive target. */
public final class BackupRepair {
    private BackupRepair() {}
    static List<Object> key(Transaction t) {
        return Arrays.asList(t.business,t.type,t.amount,t.category==null?"":t.category,t.note==null?"":t.note,t.date);
    }
    public static final class Plan {
        public final List<Transaction> reference,targets,retained;
        public final int missing,changed,withoutOriginal,alreadyArchived;
        ReviewedRepair reviewed;
        private Plan(List<Transaction> reference,List<Transaction> targets,List<Transaction> retained,int missing,int changed,int withoutOriginal,int alreadyArchived) {
            this.reference=Collections.unmodifiableList(new ArrayList<>(reference));
            this.targets=Collections.unmodifiableList(targets);this.retained=Collections.unmodifiableList(retained);
            this.missing=missing;this.changed=changed;this.withoutOriginal=withoutOriginal;this.alreadyArchived=alreadyArchived;
        }
        private static Map<String,List<Object>> snapshot(List<Transaction> rows) {
            Map<String,List<Object>> result=new TreeMap<>();
            for(Transaction t:rows){List<Object> value=new ArrayList<>(key(t));value.add(t.updatedAt);value.add(t.archived);result.put(t.syncId,value);}
            return result;
        }
        boolean sameSelection(Plan other) {
            return snapshot(targets).equals(snapshot(other.targets))&&snapshot(retained).equals(snapshot(other.retained));
        }
    }
    public static Plan plan(List<Transaction> reference,List<Transaction> current) {
        Set<String> importedIds=new HashSet<>();Map<List<Object>,List<Transaction>> groups=new LinkedHashMap<>();
        for(Transaction t:reference) {
            if(t.syncId==null||!importedIds.add(t.syncId))throw new IllegalArgumentException("Yedek kimlikleri geçersiz.");
            List<Object> content=key(t);List<Transaction> group=groups.get(content);
            if(group==null){group=new ArrayList<>();groups.put(content,group);}group.add(t);
        }
        Map<String,Transaction> byId=new HashMap<>();
        for(Transaction t:current)byId.put(t.syncId,t);
        List<Transaction> targets=new ArrayList<>(),retained=new ArrayList<>();
        int missing=0,changed=0,withoutOriginal=0,alreadyArchived=0;
        for(Map.Entry<List<Object>,List<Transaction>> group:groups.entrySet()) {
            List<Transaction> originals=new ArrayList<>();
            for(Transaction t:current)if(!t.archived&&!importedIds.contains(t.syncId)&&group.getKey().equals(key(t)))originals.add(t);
            Collections.sort(originals,(a,b)->a.syncId.compareTo(b.syncId));
            boolean enough=originals.size()>=group.getValue().size();
            boolean used=false;
            for(Transaction expected:group.getValue()) {
                Transaction actual=byId.get(expected.syncId);
                if(actual==null){missing++;continue;}
                if(!key(expected).equals(key(actual))){changed++;continue;}
                if(actual.archived){alreadyArchived++;continue;}
                if(!enough){withoutOriginal++;continue;}
                targets.add(actual);used=true;
            }
            // Preserve the original backup's multiplicity, including genuinely identical entries.
            if(used)retained.addAll(originals.subList(0,group.getValue().size()));
        }
        return new Plan(reference,targets,retained,missing,changed,withoutOriginal,alreadyArchived);
    }
    public static Plan reviewedPlan(ReviewedRepair source,List<Transaction> current) {
        Map<String,Transaction> byId=new HashMap<>();for(Transaction t:current)byId.put(t.syncId,t);
        List<Transaction> targets=new ArrayList<>(),retained=new ArrayList<>();int archived=0;
        for(Transaction expected:source.retained){
            Transaction actual=byId.get(expected.syncId);
            if(actual==null||!key(expected).equals(key(actual))||expected.updatedAt!=actual.updatedAt||expected.archived!=actual.archived)
                throw new IllegalStateException("Referans kayıt değişmiş veya eksik. Yeni cihaz yedekleriyle düzeltme yeniden incelenmeli.");
            retained.add(actual);
        }
        for(Transaction expected:source.targets){
            Transaction actual=byId.get(expected.syncId);
            if(actual==null||!key(expected).equals(key(actual))||actual.updatedAt<expected.updatedAt||(!actual.archived&&actual.updatedAt!=expected.updatedAt))
                throw new IllegalStateException("Arşivlenecek kayıt değişmiş veya eksik. Yeni cihaz yedekleriyle düzeltme yeniden incelenmeli.");
            if(actual.archived)archived++;else targets.add(actual);
        }
        Plan result=new Plan(source.targets,targets,retained,0,0,0,archived);result.reviewed=source;return result;
    }
}
