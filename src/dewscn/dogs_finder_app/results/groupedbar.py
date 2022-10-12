import sys

import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt

def plot_makespan():

    #sns.set(font_scale=1.8)
    plt.style.use('bmh')
    runs = pd.read_csv("loadbalancing_data.csv")

    g = sns.catplot(data=runs, kind="bar", x="Model Version", y="Makespan (mm:ss)", hue="Load Balancing",
    ci="sd", palette="dark", alpha=.6, height=6)
    #g.fig.set_size_inches(16, 9)
    g._legend.set_bbox_to_anchor([0.4, 0.9])
    g.fig.autofmt_xdate()
    from_time = 0
    to_time = 500000
    step_time = 100000
    g.ax.set_yticks(range(from_time,to_time,step_time), labels=[str(pd.Timedelta(x,"milliseconds"))[10:18] for x in range(from_time,to_time,step_time)])

    #TO BE SURE THAT LABELS MATCH CORRECTLY COMMENT THE NEXT SENTENCE AND LEAVE SEARBORN GENERATE LABELS AUTOMATICALLY
    #g.set_xticklabels(labels=["quantized_input32_4threads","non_quantized_4threads","quantized_input32_1thread","non_quantized_1thread"])

    plt.show()
    g.fig.savefig("loadbalancing_perf.png", format="png")


plot_makespan()