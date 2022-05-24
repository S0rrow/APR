#!/bin/bash
# patchStrategy = "flfreq"
# concretizationStrategy = "hash-match"
JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
for patchStrategy in "flfreq"; do
    for concretizationStrategy in "hash-match"; do

        # cp confix/properties/confix.properties "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"
        # echo "patch.strategy=$patchStrategy" >> "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"
        # echo "concretize.strategy=$concretizationStrategy" >> "confix/properties/confix-$patchStrategy-$concretizationStrategy.properties"

        sed -i "s/.*patch\.strategy=.*/patch\.strategy=$patchStrategy/g" /home/codemodel/leshen/APR/confix/properties/confix.properties
        sed -i "s/.*concretize\.strategy=.*/concretize\.strategy=$concretizationStrategy/g" /home/codemodel/leshen/APR/confix/properties/confix.properties

        now=$(date +"%c")
        echo "ConFix with patch strategy \"$patchStrategy\" and concretizaton strategy \"$concretizationStrategy\" run at time $now" >> different_strategy_batch.txt
        python3 -u /home/codemodel/leshen/APR/fix-target-integrated.py /home/codemodel/leshen/APR/project_argument.txt

	    # rm -rf target/batch_*

        # cat confix/properties/confix.properties
        # printf "\n"
    done
done
