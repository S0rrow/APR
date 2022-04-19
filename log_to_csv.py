import sys
import datetime as dt

def main(args):
    bugs_result = dict()
    bugs = list()

    batch_execution_log = None
    with open(args[1], 'r') as infile:
        batch_execution_log = infile.read().splitlines()
    
    for each in batch_execution_log:
        words = each.split()
        exec_time = ' '.join(words[-7:-1])
        exec_time_dt = dt.datetime.strptime(exec_time, "%a %d %b %Y %I:%M:%S %p")
        exec_time_dt_alt = exec_time_dt + dt.timedelta(seconds = 1)

        exec_time_str = exec_time_dt.strftime("%Y%m%d%H%M%S")
        exec_time_str_alt = exec_time_dt_alt.strftime("%Y%m%d%H%M%S")

        patch_idx = words.index('strategy') + 1
        patch_strategy = words[patch_idx].strip('"')
        concretization_idx = words.index('strategy', patch_idx) + 1
        concretization_strategy = words[concretization_idx].strip('"')

        log = None
        try:
            with open(f'./target/log_batch_{exec_time_str}.txt') as infile:
                log = infile.read().splitlines()
        except:
            with open(f'./target/log_batch_{exec_time_str_alt}.txt') as infile_alt:
                log = infile_alt.read().splitlines()
            
        for line in log:
            if line.startswith(('Batch', 'Total')):
                continue

            bug_id, result = line.split(':', 1)
            result = result.strip()

            if result.startswith('Pool'):
                if bug_id not in bugs_result.keys():
                    bugs_result[bug_id] = []
                    bugs.append(bug_id)
                
                bugs_result[bug_id].append("")

                bugs_result[bug_id][-1] += "P" if result.find("finished") != -1 else "F"

            elif result.startswith('ConFix'):
                if result.find("Plausible patch not found") != -1:
                    bugs_result[bug_id][-1] += "N"
                elif result == "ConFix Execution successfully finished.":
                    bugs_result[bug_id][-1] += "P"
                else:
                    bugs_result[bug_id][-1] += "F"
    
    with open("result_batch_execution.csv", "w") as outfile:
        for bug in bugs:
            outfile.write(','.join([bug] + bugs_result[bug]) + "\n")



if __name__ == '__main__':
    main(sys.argv)