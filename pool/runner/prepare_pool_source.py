import csv
import logging
import numpy as np
import os #nope
import sys
import pandas as pd


def main(argv):
    projects = ["Closure", "Lang", "Math", "Time"]
    root = os.getcwd()
    code_dir = root+"/pool/prepare_pool_source"

    #simfin 결과 읽기
    read_input = pd.read_csv(root+"/pool/outputs/simfin/test_result.csv", 
                            names=['Y_BIC_SHA', 'Y_BIC_Path', 'Y_Project', 'Y_BugId',
                                   'Rank', 'Sim-Score', 'Y^_Project', 
                                   'Y^_BIC_SHA', 'Y^_BIC_Path', 'Y^_BFC_SHA', 'Y^_BFC_Path', 'Y^_BFC_Hunk'])
    input_csv = read_input.values



    for i in range(len(read_input)):
        if i==0:
            continue
        ## 각 열에서 필요한 정보 읽어오기
        y_bic_sha = input_csv[i][0]
        y_project = input_csv[i][2]
        y_bugId = input_csv[i][3]
        rank = input_csv[i][4]
        #sim_score = input_csv[i][5]    # no need
        yhat_project = input_csv[i][6]
        yhat_bic_sha = input_csv[i][7]
        yhat_bic_path = input_csv[i][8]
        yhat_bfc_sha = input_csv[i][9]
        yhat_bfc_path = input_csv[i][10]
        #yhat_bfc_hunk = input_csv[i][11]   # no need

        D4J_ID = y_project + y_bugId

        buggy_sha = ""
        clean_sha = ""
        buggy_path = ""


        each_dir = code_dir+"/"+y_project+"-"+y_bugId
        os.system("mkdir "+each_dir)


        ## check out the reccomended commit in the repository and copy the file into code_dir
        rec_BIC_path = each_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_old.java"
        rec_BFC_path = each_dir+"/"+y_project+"_"+y_bugId+"_rank-"+rank+"_new.java"

        os.system("cd ~/APR_Projects/data/AllBIC/reference/repositories/"+yhat_project+" ; "
                + "git checkout -f "+yhat_bic_sha+" ; "
                + "cp "+yhat_bic_path+" "+rec_BIC_path+" ; "
                + "git checkout -f "+yhat_bfc_sha+" ; "
                + "cp "+yhat_bfc_path+" "+rec_BFC_path+" ; " )
        

        print('Finished ALL!!')



if __name__ == '__main__':
    main(sys.argv)