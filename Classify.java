import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;

import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSink;
import weka.classifiers.functions.LibSVM;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Normalize;
import weka.classifiers.Evaluation;
public class Classify {
	
	private String filepath;//用于分类的全部文件所在的文件夹路径
	private double percent;//抽样的百分比，用于做训练集
	private int num;//重复抽样的次数
	private int obj_num;//样本个数
	private int classifier_num;//基分类器的个数
	private char[] flag;//记录每个样本的隶属，0表示尚未分配，1表示属于训练集，2表示属于验证集，3表示属于测试集
	private ArrayList<String> dec_classname_list=new ArrayList<String>();//记录类别名
	private ArrayList<ArrayList<Integer>> class_obj_list=new ArrayList<ArrayList<Integer>>();//记录每类样本对应的对象id列表
	private int train_obj_num;//记录训练集对象个数
	private int validation_obj_num;
	private int test_obj_num;
	private ArrayList<String> old_label=new ArrayList<String>();//记录测试集的原始标签
	private ArrayList<String> new_label=new ArrayList<String>();//记录测试集的新分类标签
	private double acc_list[];//记录基分类器的分类正确率
	private int num_list[];//记录每个基分类器对应的属性个数
	private int svm_support_num[][];
	private double end_acc=0.0;//记录num次平均集成分类器分类正确率
	private double end_tp=0.0;
	private double end_tn=0.0;
	private double end_fn=0.0;
	private double end_fp=0.0;
	//private char GR_flag;//粒度约简标志，'O'表示只需一个粒度（GO标号）即可满足全体GO的正域的情况；'M'表示需要多个
	private ArrayList<String> reduction_list=new ArrayList<String>();
	private double kappastatistic[];
	private double kappanumber=0;
	private int number=0;
	private int tp=0;
	private int tn=0;
	private int fn=0;
	private int fp=0;
	public Classify(String path,double per,int n,double kappanumber){
		this.filepath=path;
		this.percent=per;
		this.num=n;
		this.train_obj_num=0;
		this.kappanumber=kappanumber;
	}
	
	/*public Classify(String path,int n){
		this.filepath=path;
		this.num=n;
		this.train_obj_num=0;
	
	}*/
	
	public String getFilepath() {
		return filepath;
	}

	public double getPercent() {
		return percent;
	}

	public int getNum() {
		return num;
	}

	public int getObj_num() {
		return obj_num;
	}

	public int getClassifier_num(){
		return this.classifier_num;
	}

	public char[] getFlag() {
		return flag;
	}

	public ArrayList<String> getDec_classname_list() {
		return dec_classname_list;
	}

	public ArrayList<ArrayList<Integer>> getClass_obj_list() {
		return class_obj_list;
	}

	public int getTrain_obj_num() {
		return train_obj_num;
	}

	public ArrayList<String> getOld_label() {
		return old_label;
	}

	public ArrayList<String> getNew_label() {
		return new_label;
	}

	public double getEnd_acc() {
		return end_acc;
	}

	public double[] getAcc_list(){
		return this.acc_list;
	}

	public int[][] getSvm_support_num(){
		return svm_support_num;
	}
	
	//读列表中的一个文件，获取样本信息
	public void read_info()throws Exception{
		//只读取文件夹中的一个文件
		File file=new File(this.filepath);
		String[] file_list=file.list();
		/*for(int i=0;i<file_list.length;i++){
			String[] split=file_list[i].split("\\.");
			//System.out.println(split[0].substring(7));
			this.reduction_list.add(split[0].substring(7));
		}*/
		this.classifier_num=file_list.length;
		this.acc_list=new double[file_list.length];//初始化分类正确率列表，记录基分类器的分类正确率
		this.num_list=new int[file_list.length];//初始化属性个数列表，记录基分类器对应的属性个数
		FileInputStream in=new FileInputStream(this.filepath+"\\"+file_list[0]);
		InputStreamReader inReader=new InputStreamReader(in);
		BufferedReader bReader=new BufferedReader(inReader);
		String line=null;
		int flag=0;
		while((line=bReader.readLine())!=null){
			if(line!=""){
				if(line.substring(0, 5).toUpperCase().equals("@DATA")){//找到@DATA行
					flag=1;
					break;
				}
				else if(line.substring(0, 10).toUpperCase().equals("@ATTRIBUTE")){
					String split[]=line.split(" ");
					if(split[1].toUpperCase().equals("CLASS")){//添加类别列表
						String temp=line.substring(18,line.length()-1);
						String split1[]=temp.split(",");
						for(int i=0;i<split1.length;i++){
							split1[i]=split1[i].trim();
							dec_classname_list.add(split1[i]);
						}
					}
				}
			}
		}
		//初始化类对象列表集
		for(int i=0;i<this.dec_classname_list.size();i++){
			ArrayList<Integer> temp=new ArrayList<Integer>();
			this.class_obj_list.add(temp);
		}
		if(flag==1){//读数据对象总个数及每个对象所属属性列表
			int i=0;
			while((line=bReader.readLine())!=null){
				String split[]=line.split(",");
				String obj_label=split[split.length-1].trim();
				for(int k=0;k<this.dec_classname_list.size();k++){
					String temp_class=this.dec_classname_list.get(k);
					if(obj_label.equals(temp_class)){
						ArrayList<Integer> temp_list=this.class_obj_list.get(k);
						temp_list.add(i);
						this.class_obj_list.set(k, temp_list);
					}
				}
				i++;
			}
			this.obj_num=i;
		}
		bReader.close();
		inReader.close();
		in.close();
	}
	
	//无放回抽样
	public void sampling(){
		//初始化
		int o_num=this.obj_num;
		flag=new char[o_num];
		for(int i=0;i<o_num;i++){
			flag[i]='0';
		}
		this.train_obj_num=0;
		//在每类对象中分别抽取
		for(int k=0;k<this.dec_classname_list.size();k++){
			ArrayList<Integer> temp_list=this.class_obj_list.get(k);//获得当前类的对象列表
			int class_obj_num=temp_list.size();
			//System.out.println("class_obj_num"+class_obj_num);
			int train_num=(int)(this.percent*class_obj_num);//训练集样本个数
			int validation_num=(int)(0.2*class_obj_num);
			//System.out.println("train_num"+train_num);
			this.train_obj_num=this.train_obj_num+train_num;
			this.validation_obj_num=validation_obj_num+validation_num;
			for(int m=0,n=0;m<class_obj_num || n<train_num;m++){
				int r=(int)(Math.random()*(class_obj_num-m));
				//System.out.println("r"+r);
				if(r<(train_num-n)){
					flag[temp_list.get(m)]='1';
					n++;
					//System.out.println(j);
				}
				else{
					flag[temp_list.get(m)]='2';
				}
			}
			ArrayList<Integer> temp_list1=new ArrayList<Integer>();
			Iterator<Integer> it =temp_list1.iterator();  
	        for(;it.hasNext();) {  
	              it.next();  
	              it.remove(); 
	        }
			for(int i=0;i<class_obj_num;i++){
				if(flag[temp_list.get(i)]=='2'){
					temp_list1.add(temp_list.get(i));
				}
			}
			for(int i=0,j=0;i<temp_list1.size()||j<validation_num;i++){
				int r=(int)(Math.random()*(temp_list1.size()-i));
				if(r<(validation_num-j)){
					j++;
					//System.out.println(j);
				}
				else{
					flag[temp_list1.get(i)]='3';
				}
			}	
		}
	}
	
	//leave one out of抽样
	/*public void leave_one_sampling(int id){
		//初始化
		int o_num=this.obj_num;
		flag=new char[o_num];
		for(int i=0;i<o_num;i++){
			flag[i]='0';
		}
		this.train_obj_num=this.obj_num-1;
		//将当前id对象标记成测试集，其他对象为训练集
		for(int i=0;i<this.obj_num;i++){
			if(i==id){
				flag[i]='2';
			}
			else{
				flag[i]='1';
			}
		}
	}*/
	
	public double classifier(){
		int test_num=this.obj_num-this.train_obj_num-this.validation_obj_num;
		System.out.println(test_num);
		this.svm_support_num=new int[test_num][this.dec_classname_list.size()];
		this.kappastatistic=new double[121];//记录训练集中每个样本的类标签支持度
		this.old_label.clear();
		this.new_label.clear();
		int acc_num=0;//记录判断正确的个数
		double acc=0.0;//记录正确率
		
		try{
			
			//遍历文件夹中的每一个数据文件
			File file=new File(this.filepath);
			String[] file_list=file.list();
			for(int i=0;i<file_list.length;i++){
				String path=this.filepath+"\\"+file_list[i];
				
				//读取数据
				FileReader data=new FileReader(path);
				Instances m_instances = new Instances(data);
				m_instances.setClassIndex( m_instances.numAttributes() - 1 );//设置该实体的类别属性
				this.num_list[i]=m_instances.numAttributes()-1;//记录属性个数，除决策属性外
				System.out.println("读取数据成功");
				
				//进行数据标准化-1~1
				Normalize normalize=new Normalize();
				String options[]=weka.core.Utils.splitOptions("-S 2.0 -T -1.0");//设置参数
				normalize.setOptions(options);
				normalize.setInputFormat(m_instances);//设置输入文件
				Instances n_m_instances=Filter.useFilter(m_instances, normalize);//使用过滤器并生成新的数据
				//DataSink.write(this.filepath+"\\nor_"+file_list[i],n_m_instances);//输出标准化后的数据
				System.out.println("数据标准化成功");
				
				//根据抽样结果，构建训练集和测试集
				Instances train_instances = new Instances(n_m_instances);//记录训练集，初始化为全集
				Instances test_instances = new Instances(n_m_instances);//记录测试集，初始化为全集
				Instances validation_instances=new Instances(n_m_instances);
				train_instances.setClassIndex( train_instances.numAttributes() - 1 );
				test_instances.setClassIndex( test_instances.numAttributes() - 1 );
				validation_instances.setClassIndex(validation_instances.numAttributes()-1);
				for(int j=this.obj_num-1;j>=0;j--){
					if(this.flag[j]=='1'){
						test_instances.delete(j);
						validation_instances.delete(j);
					}
					else if(this.flag[j]=='2'){
						train_instances.delete(j);
						test_instances.delete(j);
					}
					else if(this.flag[j]=='3'){
						train_instances.delete(j);
						validation_instances.delete(j);
					}
					else{
						System.out.println("数据集分裂有误！");
					}
				}
				//DataSink.write(this.filepath+"\\train_"+file_list[i],train_instances);
				//DataSink.write(this.filepath+"\\test_"+file_list[i],test_instances);
				System.out.println("数据集分裂成功");
				
				//用训练集，构建SVM分类器
				Classifier svm= new LibSVM();
				svm.buildClassifier(train_instances);
				Evaluation eval=new Evaluation(train_instances);
				eval.evaluateModel(svm,validation_instances);
				kappastatistic[i]=eval.kappa();
				if(kappastatistic[i]>this.kappanumber){
					 number=number+1;
				}
				//测试，并记录对测试集样本的分类结果
				for(int k=0;k<test_instances.numInstances();k++){
					double t=test_instances.instance(k).classValue();
					String temp_old_label=test_instances.classAttribute().value((int)t);
					if(i==0){
						this.old_label.add(temp_old_label);//添加旧标签
					}
					double class_l=svm.classifyInstance(test_instances.instance(k));//分类
					String class_label=test_instances.classAttribute().value((int)class_l);//获得类标签
					
					//记录结果
					if(class_label.equals(temp_old_label)){
						this.acc_list[i]=this.acc_list[i]+((double)1)/test_num;
					}
					for(int m=0;m<this.dec_classname_list.size();m++){
						String temp_class=this.dec_classname_list.get(m);
						if((class_label.trim().equals(temp_class))&&(kappastatistic[i]>this.kappanumber)){
							this.svm_support_num[k][m]=this.svm_support_num[k][m]+1;
						}
					}
				}	
			}
			
			//'M'的情况，计算集成分类正确率
			//if(this.GR_flag=='M'){
				for(int x=0;x<test_num;x++){
					//设置新类别标签为基分类器投票最多的类
					int max=this.svm_support_num[x][0];//记录类的最大支持数，初始化为第一个类的支持数
					int max_id=0;//记录取最大值时的类id
					for(int y=1;y<this.dec_classname_list.size();y++){
						if(this.svm_support_num[x][y]>max){
							max=this.svm_support_num[x][y];
							max_id=y;
						}
					}
					this.new_label.add(x,this.dec_classname_list.get(max_id));
					
					//与原标签比较，若一致，则正确个数加1
					if(this.old_label.get(x).equals(this.new_label.get(x))){
						acc_num++;
						if(this.old_label.get(x).equals("control")){
							tp=tp+1;	
						}
						if(this.old_label.get(x).equals("hypoxia")){
							tn=tn+1;
					    }
				   }
					if(!(this.old_label.get(x).equals(this.new_label.get(x)))){
						if(this.old_label.get(x).equals("control")){
							fn=fn+1;	
						}
						if(this.old_label.get(x).equals("hypoxia")){
							fp=fp+1;
					    }
				   }
				}
				acc=((double)acc_num)/test_num;
			//}
			
			//输出各个基分类器分类正确率
			System.out.println("各个基分类器累计acc和kappa系数");
			for(int k=0;k<this.acc_list.length;k++){
				System.out.println(this.acc_list[k]+";"+kappastatistic[k]);
			}
			System.out.print("\n");
				}
				catch(Exception e){
			       e.printStackTrace();
		}
		return acc;
	}
	
	public void printresult(){
		try{
			File path=new File(this.filepath);
			String threshold=path.getName();
			String up_path=path.getParentFile().getParent();
			String path1=up_path+"\\Result\\";
			File r=new File(path1);
		    if(!r.exists()){
		    	r.mkdirs();
		    } 
            String newfile=path1+"Acc.txt";
			//System.out.println(newfile);
			File fout = new File(newfile);
			if(!fout.exists()){
				BufferedWriter bwriter= new BufferedWriter(new FileWriter(fout,true));
				bwriter.write("number\t"+"threshold\t"+"kappa"+"\titer\tavg_em_acc"+"\t"+"TP"+"\t"+"TN"+"\t"+"FN"+"\t"+"FP"+"\t"+"classifier_num"+"\t"+"avg_acc\r\n");
				bwriter.close();
			}
			BufferedWriter bwriter= new BufferedWriter(new FileWriter(fout,true));
			String line=this.number+"\t"+threshold+"\t"+this.kappanumber+"\t"+this.num+"\t"+this.end_acc+"\t"+this.end_tp+"\t"+this.end_tn+"\t"+this.end_fn+"\t"+this.end_fp+"\t"+this.classifier_num+"\t";
			for(int i=0;i<this.classifier_num-1;i++){
				line=line+this.acc_list[i]+"("+this.num_list[i]+")";//+"["+this.reduction_list.get(i)+"]"+";";
			}
			line=line+this.acc_list[this.classifier_num-1]+"("+this.num_list[this.classifier_num-1]+")";//["+this.reduction_list.get(this.classifier_num-1)+"]";
			bwriter.write(line+"\r\n");
			bwriter.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void run(){
		try{
			this.read_info();
			System.out.println("读信息成功！");
			for(int i=0;i<this.num;i++){
				this.train_obj_num=0;
				this.validation_obj_num=0;
				this.sampling();//抽样
				
				//this.leave_one_sampling(i);//leave one
				System.out.println("第"+(i+1)+"次抽样成功！");
				//打印抽样结果
				char[] f=this.getFlag();
				for(int j=0;j<f.length;j++){
					System.out.print(f[j]+";");
				}
				System.out.println("\n");
				double temp_acc=this.classifier();
				System.out.println("第"+(i+1)+"次分类成功！");
				System.out.println("temp_acc:"+temp_acc);
				this.end_acc+=temp_acc;
			}
			for(int i=0;i<this.classifier_num;i++){
				this.acc_list[i]=this.acc_list[i]/this.num;
				System.out.println("acc_list"+"["+i+"]"+acc_list[i]);
			}
			this.end_acc=this.end_acc/this.num;
			this.end_tp=((double)tp)/this.num;
			this.end_tn=((double)tn)/this.num;
			this.end_fn=((double)fn)/this.num;
			this.end_fp=((double)fp)/this.num;
			/*else if(this.GR_flag=='O'){//找最大值
				this.end_acc=this.acc_list[0];
				for(int k=1;k<this.classifier_num;k++){
					if(this.acc_list[k]>this.end_acc){
						this.end_acc=this.acc_list[k];
					}
				}
			}*/
			System.out.println("end_acc:"+this.end_acc);
			System.out.println("分类成功");
			this.printresult();
			System.out.println("打印结果成功");
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}
